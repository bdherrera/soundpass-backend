package co.soundpass.config;

import co.soundpass.modelo.RutaRelativa;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;


@Component
public class MongoEsquema implements ApplicationRunner {

    public static final String RESENAS = "reseñas";
    public static final String PERFILES_ARTISTA = "perfiles_artista";
    public static final String DETALLE_EVENTO = "detalle_evento";
    public static final String LOGS = "logs_aplicacion";
    public static final String COLA_EVENTO = "cola_evento";
    public static final String TURNOS_COLA = "turnos_cola";
    public static final int VERSION = 1;
    /** Entradas por bucket de logs: acota el tamaño de cada documento. */
    public static final int TAMANO_BUCKET_LOGS = 100;

    private static final Logger log = LoggerFactory.getLogger(MongoEsquema.class);

    private final MongoTemplate mongo;
    private volatile boolean listo;

    public MongoEsquema(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            asegurarListo();
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar MongoDB al arrancar; se reintentará en la primera escritura: {}",
                    e.getMessage());
        }
    }

    /** Prepara el esquema si aún no se hizo; se llama antes de escribir en MongoDB. */
    public void asegurarListo() {
        if (!listo) {
            synchronized (this) { // varios hilos (peticiones y escritor de logs) pueden llegar a la vez
                if (!listo) {
                    asegurar();
                    listo = true;
                }
            }
        }
    }

    public synchronized void asegurar() {
        validador(RESENAS, esquemaResenas());
        validador(PERFILES_ARTISTA, esquemaPerfilesArtista());
        validador(DETALLE_EVENTO, esquemaDetalleEvento());
        validador(LOGS, esquemaLogs());
        validador(COLA_EVENTO, esquemaColaEvento());
        validador(TURNOS_COLA, esquemaTurnosCola());

        var resenas = mongo.indexOps(RESENAS);
        resenas.ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).on("id_usuario", Sort.Direction.ASC)
                .unique().named("ux_resena_evento_usuario"));
        resenas.ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).on("fecha", Sort.Direction.DESC)
                .named("ix_resena_evento_fecha"));
        mongo.indexOps(PERFILES_ARTISTA)
                .ensureIndex(new Index().on("id_artista", Sort.Direction.ASC).unique().named("ux_perfil_artista"));
        var detalle = mongo.indexOps(DETALLE_EVENTO);
        detalle.ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).unique().named("ux_detalle_evento"));
        detalle.ensureIndex(new Index().on("etiquetas", Sort.Direction.ASC).named("ix_detalle_etiquetas"));

        // Patrón Bucket: busca el bucket abierto de la hora; los vencidos los borra el índice TTL
        var logs = mongo.indexOps(LOGS);
        logs.ensureIndex(new Index().on("hora", Sort.Direction.DESC).on("cantidad", Sort.Direction.ASC)
                .named("ix_logs_hora_cantidad"));
        logs.ensureIndex(new Index().on("expira_en", Sort.Direction.ASC).expire(0, TimeUnit.SECONDS)
                .named("ttl_logs_expira_en"));

        // Patrón Computed: un documento de contadores por evento y un turno por usuario
        mongo.indexOps(COLA_EVENTO)
                .ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).unique().named("ux_cola_evento"));
        var turnos = mongo.indexOps(TURNOS_COLA);
        turnos.ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).on("turno", Sort.Direction.ASC)
                .unique().named("ux_turno_evento_numero"));
        turnos.ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).on("id_usuario", Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("activo").is(true)))
                .named("ux_turno_activo_usuario"));
        turnos.ensureIndex(new Index().on("id_evento", Sort.Direction.ASC).on("estado", Sort.Direction.ASC)
                .on("expira_en", Sort.Direction.ASC).named("ix_turno_estado_expira"));
    }

    private void validador(String coleccion, Document esquema) {
        if (!mongo.collectionExists(coleccion)) {
            mongo.createCollection(coleccion);
        }
        mongo.getDb().runCommand(new Document("collMod", coleccion)
                .append("validator", new Document("$jsonSchema", esquema))
                .append("validationLevel", "strict")
                .append("validationAction", "error"));
    }

    private static Document esquemaResenas() {
        return objeto(List.of("id_evento", "id_usuario", "usuario", "calificacion", "fecha", "version_esquema"),
                new Document("id_evento", entero(1, null))
                        .append("id_usuario", entero(1, null))
                        .append("usuario", objeto(List.of("nombre"),
                                new Document("nombre", new Document("bsonType", "string").append("minLength", 1))))
                        .append("calificacion", entero(1, 5))
                        .append("comentario", new Document("bsonType", List.of("string", "null"))
                                .append("maxLength", 2000))
                        .append("fecha", new Document("bsonType", "date"))
                        .append("version_esquema", entero(1, null)));
    }

    private static Document esquemaPerfilesArtista() {
        return objeto(List.of("id_artista", "version_esquema"),
                new Document("id_artista", entero(1, null))
                        .append("biografia", new Document("bsonType", List.of("string", "null")))
                        .append("redes", new Document("bsonType", List.of("object", "null"))
                                .append("additionalProperties", new Document("bsonType", "string")))
                        .append("enlaces", listaDeTextos(false))
                        .append("galeria", listaDeTextos(true))
                        .append("version_esquema", entero(1, null)));
    }

    private static Document esquemaDetalleEvento() {
        return objeto(List.of("id_evento", "version_esquema"),
                new Document("id_evento", entero(1, null))
                        .append("descripcion", new Document("bsonType", List.of("string", "null")))
                        .append("etiquetas", listaDeTextos(false))
                        .append("politicas", new Document("bsonType", List.of("string", "null")))
                        .append("medios", listaDeTextos(true))
                        .append("version_esquema", entero(1, null)));
    }

    /** Bucket de logs: hasta TAMANO_BUCKET entradas de una misma hora. */
    private static Document esquemaLogs() {
        Document entrada = objeto(List.of("fecha", "nivel", "logger", "mensaje"),
                new Document("fecha", new Document("bsonType", "date"))
                        .append("nivel", new Document("bsonType", "string")
                                .append("enum", List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE")))
                        .append("logger", new Document("bsonType", "string"))
                        .append("hilo", new Document("bsonType", List.of("string", "null")))
                        .append("mensaje", new Document("bsonType", "string").append("maxLength", 2000)));
        return objeto(List.of("hora", "cantidad", "entradas", "expira_en", "version_esquema"),
                new Document("hora", new Document("bsonType", "date"))
                        .append("cantidad", entero(1, TAMANO_BUCKET_LOGS))
                        .append("entradas", new Document("bsonType", "array").append("maxItems", TAMANO_BUCKET_LOGS)
                                .append("items", entrada))
                        .append("expira_en", new Document("bsonType", "date"))
                        .append("version_esquema", entero(1, null)));
    }

    /** Contadores precalculados de la cola de un evento (patrón Computed). */
    private static Document esquemaColaEvento() {
        List<String> numero = List.of("int", "long");
        return objeto(List.of("id_evento", "habilitada", "ventana", "ttl_minutos", "siguiente_turno",
                        "ultimo_admitido", "activos", "en_espera", "version_esquema"),
                new Document("id_evento", entero(1, null))
                        .append("habilitada", new Document("bsonType", "bool"))
                        .append("ventana", entero(1, 1000))
                        .append("ttl_minutos", entero(1, 120))
                        .append("siguiente_turno", new Document("bsonType", numero).append("minimum", 1))
                        .append("ultimo_admitido", new Document("bsonType", numero).append("minimum", 0))
                        .append("activos", new Document("bsonType", numero).append("minimum", 0))
                        .append("en_espera", new Document("bsonType", numero).append("minimum", 0))
                        .append("version_esquema", entero(1, null)));
    }

    private static Document esquemaTurnosCola() {
        return objeto(List.of("id_evento", "id_usuario", "turno", "estado", "activo", "entrada_en", "version_esquema"),
                new Document("id_evento", entero(1, null))
                        .append("id_usuario", entero(1, null))
                        .append("turno", new Document("bsonType", List.of("int", "long")).append("minimum", 1))
                        .append("estado", new Document("bsonType", "string").append("enum",
                                List.of("ESPERANDO", "ADMITIDO", "COMPLETADO", "EXPIRADO", "CANCELADO")))
                        .append("activo", new Document("bsonType", "bool"))
                        .append("entrada_en", new Document("bsonType", "date"))
                        .append("admitido_en", new Document("bsonType", List.of("date", "null")))
                        .append("expira_en", new Document("bsonType", List.of("date", "null")))
                        .append("version_esquema", entero(1, null)));
    }

    private static Document objeto(List<String> requeridos, Document propiedades) {
        return new Document("bsonType", "object").append("required", requeridos).append("properties", propiedades);
    }

    private static Document entero(Integer minimo, Integer maximo) {
        Document d = new Document("bsonType", "int");
        if (minimo != null) {
            d.append("minimum", minimo);
        }
        if (maximo != null) {
            d.append("maximum", maximo);
        }
        return d;
    }

    /** Lista de textos; si {@code rutas}, cada texto debe ser una ruta relativa (nunca binarios ni URLs). */
    private static Document listaDeTextos(boolean rutas) {
        Document item = new Document("bsonType", "string");
        if (rutas) {
            item.append("pattern", RutaRelativa.PATRON);
        }
        return new Document("bsonType", List.of("array", "null")).append("items", item);
    }
}
