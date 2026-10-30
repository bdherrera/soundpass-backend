package co.soundpass.servicio.impl;

import co.soundpass.servicio.LogsServicio;
import co.soundpass.servicio.LogsServicio.EntradaLog;
import co.soundpass.config.MongoEsquema;
import co.soundpass.vista.LogDto;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Logs de la aplicación en MongoDB con el patrón Bucket: cada documento agrupa hasta
 * {@link MongoEsquema#TAMANO_BUCKET_LOGS} entradas de una misma hora (en vez de un documento por línea), así hay
 * menos documentos e índices y la consulta por rango de horas es barata. Un índice TTL borra los buckets vencidos.
 *
 * <p>Escribir un log nunca debe retrasar ni romper una petición: las entradas pasan por una cola en memoria
 * acotada y las guarda un hilo aparte. Si MongoDB no responde o la cola se llena, la entrada se descarta.
 */
@Service
public class LogsServicioImpl implements LogsServicio {

    private static final Logger log = LoggerFactory.getLogger(LogsServicio.class);
    private static final int CAPACIDAD_COLA = 5000;
    private static final long SEGUNDOS_ENTRE_AVISOS = 60;

    private final MongoTemplate mongo;
    private final MongoEsquema esquema;
    private final int retencionDias;
    private final BlockingQueue<EntradaLog> cola = new ArrayBlockingQueue<>(CAPACIDAD_COLA);
    private final AtomicLong encoladas = new AtomicLong();
    private final AtomicLong procesadas = new AtomicLong();
    private volatile Instant ultimoAviso = Instant.EPOCH;
    private Thread escritor;

    public LogsServicioImpl(MongoTemplate mongo, MongoEsquema esquema,
                        @Value("${soundpass.logs.retencion-dias:30}") int retencionDias) {
        this.mongo = mongo;
        this.esquema = esquema;
        this.retencionDias = retencionDias;
    }

    @PostConstruct
    void iniciar() {
        escritor = new Thread(this::escribirSiempre, "logs-mongo");
        escritor.setDaemon(true);
        escritor.start();
    }

    @PreDestroy
    void detener() {
        escritor.interrupt();
    }

    /** No bloquea: si la cola está llena, la entrada se pierde. */
    @Override
    public void encolar(EntradaLog entrada) {
        encoladas.incrementAndGet();
        if (!cola.offer(entrada)) {
            encoladas.decrementAndGet();
        }
    }

    /** Espera a que se guarden las entradas encoladas hasta ahora. Pensado para pruebas. */
    @Override
    public boolean esperarEscrituras(long maximoMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + maximoMs;
        while (procesadas.get() < encoladas.get()) {
            if (System.currentTimeMillis() > limite) {
                return false;
            }
            Thread.sleep(20);
        }
        return true;
    }

    /** Entradas más recientes primero, entre {@code desde} y {@code hasta} (por omisión, las últimas 24 horas). */
    @Override
    public List<LogDto> consultar(String nivel, Instant desde, Instant hasta, int limite) {
        Instant fin = hasta != null ? hasta : Instant.now();
        Instant inicio = desde != null ? desde : fin.minus(24, ChronoUnit.HOURS);
        Document filtroEntrada = new Document("entradas.fecha",
                new Document("$gte", Date.from(inicio)).append("$lte", Date.from(fin)));
        if (nivel != null) {
            filtroEntrada.append("entradas.nivel", nivel);
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document("hora",
                        new Document("$gte", Date.from(inicio.truncatedTo(ChronoUnit.HOURS)))
                                .append("$lte", Date.from(fin)))),
                new Document("$unwind", "$entradas"),
                new Document("$match", filtroEntrada),
                new Document("$sort", new Document("entradas.fecha", -1)),
                new Document("$limit", limite),
                new Document("$replaceRoot", new Document("newRoot", "$entradas")));

        List<LogDto> resultado = new ArrayList<>();
        for (Document d : mongo.getCollection(MongoEsquema.LOGS).aggregate(pipeline)) {
            resultado.add(new LogDto(d.getDate("fecha").toInstant().atOffset(ZoneOffset.UTC), d.getString("nivel"),
                    d.getString("logger"), d.getString("hilo"), d.getString("mensaje")));
        }
        return resultado;
    }

    private void escribirSiempre() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                EntradaLog entrada = cola.take();
                try {
                    guardar(entrada);
                } catch (RuntimeException e) {
                    avisar(e);
                } finally {
                    procesadas.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Agrega la entrada al bucket abierto de su hora; si ese bucket ya está lleno, crea uno nuevo (upsert). */
    private void guardar(EntradaLog e) {
        esquema.asegurarListo();
        Document entrada = new Document("fecha", Date.from(e.fecha())).append("nivel", e.nivel())
                .append("logger", e.logger()).append("hilo", e.hilo()).append("mensaje", e.mensaje());
        mongo.getCollection(MongoEsquema.LOGS).updateOne(
                Filters.and(Filters.eq("hora", Date.from(e.fecha().truncatedTo(ChronoUnit.HOURS))),
                        Filters.lt("cantidad", MongoEsquema.TAMANO_BUCKET_LOGS)),
                Updates.combine(Updates.push("entradas", entrada), Updates.inc("cantidad", 1),
                        Updates.setOnInsert("expira_en", Date.from(Instant.now().plus(retencionDias, ChronoUnit.DAYS))),
                        Updates.setOnInsert("version_esquema", MongoEsquema.VERSION)),
                new UpdateOptions().upsert(true));
    }

    /** Un aviso por minuto como máximo, para que una caída de MongoDB no llene el log. */
    private void avisar(RuntimeException e) {
        Instant ahora = Instant.now();
        if (ahora.isAfter(ultimoAviso.plusSeconds(SEGUNDOS_ENTRE_AVISOS))) {
            ultimoAviso = ahora;
            log.warn("No se pudieron guardar logs en MongoDB: {}", e.getMessage());
        }
    }
}
