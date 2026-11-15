package co.soundpass.servicio.impl;

import co.soundpass.servicio.ColaServicio;
import co.soundpass.seguridad.Sesion;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.setOnInsert;

import co.soundpass.config.MongoEsquema;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.repositorio.EventoRepository;
import co.soundpass.vista.ColaConfigEntrada;
import co.soundpass.vista.ColaEstadoDto;
import co.soundpass.vista.TurnoDto;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Cola de reserva de un evento (patrón Computed de MongoDB). En vez de contar turnos cada vez que alguien pregunta
 * por la cola, el documento de {@code cola_evento} guarda contadores precalculados que se actualizan de forma
 * atómica en cada escritura:
 * <ul>
 *   <li>{@code siguiente_turno}: el número que recibirá quien entre;</li>
 *   <li>{@code en_espera}: cuántos turnos esperan ser admitidos;</li>
 *   <li>{@code activos}: cuántos admitidos están comprando ahora (nunca más que {@code ventana});</li>
 *   <li>{@code ultimo_admitido}: el turno más alto admitido hasta ahora.</li>
 * </ul>
 * Así, el estado de la cola se lee de un solo documento y la posición de un turno es
 * {@code turno - ultimo_admitido}, sin contar documentos.
 *
 * <p>Admitir es reservar un lugar con un incremento condicional atómico ({@code activos < ventana} y
 * {@code en_espera > 0}) y pasar a ADMITIDO el turno ESPERANDO más antiguo; si no existe ninguno (su documento aún
 * no se insertó) se devuelve el lugar. Los admitidos que no compran vencen a los {@code ttl_minutos}; los
 * vencimientos se procesan al consultar la cola.
 *
 * <p>PostgreSQL sigue siendo la fuente de verdad: si MongoDB falla, la compra no depende de la cola (ver
 * {@link #verificarAcceso}).
 */
@Service
public class ColaServicioImpl implements ColaServicio {

    private static final Logger log = LoggerFactory.getLogger(ColaServicio.class);
    private static final int DUPLICADO = 11000;
    private static final int MAXIMO_ADMISIONES = 1000;
    private static final int MAXIMO_DEVOLUCIONES_SEGUIDAS = 3;

    private final MongoTemplate mongo;
    private final MongoEsquema esquema;
    private final EventoRepository eventos;

    public ColaServicioImpl(MongoTemplate mongo, MongoEsquema esquema, EventoRepository eventos) {
        this.mongo = mongo;
        this.esquema = esquema;
        this.eventos = eventos;
    }

    private MongoCollection<Document> cola() {
        return mongo.getCollection(MongoEsquema.COLA_EVENTO);
    }

    private MongoCollection<Document> turnos() {
        return mongo.getCollection(MongoEsquema.TURNOS_COLA);
    }

    // ------------------------------------------------------------------ administración

    /** Crea o actualiza la configuración de la cola. Los contadores solo se inicializan la primera vez. */
    @Override
    public ColaEstadoDto configurar(Integer idEvento, ColaConfigEntrada datos) {
        if (!eventos.existsById(idEvento)) {
            throw ApiException.noEncontrado("El evento no existe");
        }
        esquema.asegurarListo();
        cola().updateOne(eq("id_evento", idEvento),
                combine(set("habilitada", datos.habilitada()), set("ventana", datos.ventana()),
                        set("ttl_minutos", datos.ttlMinutos()), setOnInsert("siguiente_turno", 1L),
                        setOnInsert("ultimo_admitido", 0L), setOnInsert("activos", 0L),
                        setOnInsert("en_espera", 0L), setOnInsert("version_esquema", MongoEsquema.VERSION)),
                new UpdateOptions().upsert(true));
        return estado(idEvento);
    }

    /** Lee los contadores precalculados; no cuenta turnos. */
    @Override
    public ColaEstadoDto estado(Integer idEvento) {
        mantener(idEvento);
        Document c = cola().find(eq("id_evento", idEvento)).first();
        if (c == null) {
            return new ColaEstadoDto(idEvento, false, 0, 0, 0, 0);
        }
        return new ColaEstadoDto(idEvento, c.getBoolean("habilitada"), c.getInteger("ventana"),
                c.getInteger("ttl_minutos"), numero(c, "en_espera"), numero(c, "activos"));
    }

    // ------------------------------------------------------------------------ usuario

    /** Entra a la cola (idempotente: si ya tiene un turno activo, lo devuelve). */
    @Override
    public TurnoDto unirse(Integer idEvento) {
        Integer idUsuario = Sesion.idUsuarioActual();
        Document c = cola().find(eq("id_evento", idEvento)).first();
        if (c == null || !c.getBoolean("habilitada")) {
            throw ApiException.conflicto(CodigoError.COLA_INACTIVA, "El evento no tiene cola de reserva activa");
        }
        mantener(idEvento);
        Document existente = turnoActivo(idEvento, idUsuario);
        if (existente != null) {
            return aDto(idEvento, existente);
        }

        esquema.asegurarListo();
        // un solo $inc atómico reparte el turno y suma a la espera: dos usuarios nunca reciben el mismo número
        Document despues = cola().findOneAndUpdate(eq("id_evento", idEvento),
                new Document("$inc", new Document("siguiente_turno", 1L).append("en_espera", 1L)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        long turno = numero(despues, "siguiente_turno") - 1;
        try {
            turnos().insertOne(new Document("id_evento", idEvento).append("id_usuario", idUsuario)
                    .append("turno", turno).append("estado", "ESPERANDO").append("activo", true)
                    .append("entrada_en", new Date()).append("admitido_en", null).append("expira_en", null)
                    .append("version_esquema", MongoEsquema.VERSION));
        } catch (MongoWriteException e) {
            if (e.getCode() != DUPLICADO) {
                throw e;
            }
            // dos solicitudes simultáneas del mismo usuario: gana el índice único y esta devuelve el turno ganador
            ajustar(idEvento, "en_espera", -1);
            return aDto(idEvento, turnoActivo(idEvento, idUsuario));
        }
        mantener(idEvento);
        return aDto(idEvento, turnoActivo(idEvento, idUsuario));
    }

    @Override
    public TurnoDto miTurno(Integer idEvento) {
        Integer idUsuario = Sesion.idUsuarioActual();
        mantener(idEvento);
        Document ultimo = turnos().find(and(eq("id_evento", idEvento), eq("id_usuario", idUsuario)))
                .sort(Sorts.descending("turno")).first();
        if (ultimo == null) {
            throw ApiException.noEncontrado("No tienes turno en la cola de este evento");
        }
        return aDto(idEvento, ultimo);
    }

    /** Sale de la cola (estando esperando o admitido): libera su lugar. */
    @Override
    public void salir(Integer idEvento) {
        Integer idUsuario = Sesion.idUsuarioActual();
        Document antes = turnos().findOneAndUpdate(activo(idEvento, idUsuario),
                combine(set("estado", "CANCELADO"), set("activo", false)));
        if (antes == null) {
            throw ApiException.noEncontrado("No tienes un turno activo en la cola de este evento");
        }
        ajustar(idEvento, "ADMITIDO".equals(antes.getString("estado")) ? "activos" : "en_espera", -1);
        mantener(idEvento);
    }

    // ---------------------------------------------------- integración con la compra

    /**
     * Antes de comprar: si algún evento de la compra tiene cola habilitada, el usuario debe tener un turno ADMITIDO.
     * Si MongoDB falla, la compra se permite (PostgreSQL es la fuente de verdad y la cola es opcional).
     */
    @Override
    public void verificarAcceso(Integer idUsuario, Collection<Integer> idsEventos) {
        for (Integer idEvento : idsEventos) {
            try {
                Document c = cola().find(eq("id_evento", idEvento)).first();
                if (c == null || !Boolean.TRUE.equals(c.getBoolean("habilitada"))) {
                    continue;
                }
                mantener(idEvento);
                if (turnos().countDocuments(and(activo(idEvento, idUsuario), eq("estado", "ADMITIDO"))) == 0) {
                    throw new ApiException(HttpStatus.CONFLICT, CodigoError.COLA_REQUERIDA,
                            "El evento " + idEvento + " tiene cola de reserva: entra a la cola y espera tu turno");
                }
            } catch (ApiException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("La cola de reserva no está disponible; se permite la compra: {}", e.getMessage());
            }
        }
    }

    /** Después de comprar: termina el turno y deja pasar al siguiente. Nunca hace fallar una compra ya hecha. */
    @Override
    public void completar(Integer idUsuario, Collection<Integer> idsEventos) {
        for (Integer idEvento : idsEventos) {
            try {
                Document antes = turnos().findOneAndUpdate(and(activo(idEvento, idUsuario), eq("estado", "ADMITIDO")),
                        combine(set("estado", "COMPLETADO"), set("activo", false)));
                if (antes != null) {
                    ajustar(idEvento, "activos", -1);
                    mantener(idEvento);
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo cerrar el turno del evento {}: {}", idEvento, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ mantenimiento

    /** Vence los turnos admitidos que no compraron y admite a los que esperan mientras haya lugar. */
    private void mantener(Integer idEvento) {
        if (cola().find(eq("id_evento", idEvento)).first() == null) {
            return;
        }
        long vencidos = turnos().updateMany(
                and(eq("id_evento", idEvento), eq("estado", "ADMITIDO"), lt("expira_en", new Date())),
                combine(set("estado", "EXPIRADO"), set("activo", false))).getModifiedCount();
        if (vencidos > 0) {
            ajustar(idEvento, "activos", -vencidos);
        }
        admitir(idEvento);
    }

    private void admitir(Integer idEvento) {
        // un lugar solo se reserva si hay ventana libre y alguien esperando, todo en una operación atómica
        Bson hayLugar = new Document("$expr", new Document("$and", List.of(
                new Document("$lt", List.of("$activos", "$ventana")),
                new Document("$gt", List.of("$en_espera", 0)))));
        int devoluciones = 0;
        for (int i = 0; i < MAXIMO_ADMISIONES; i++) {
            Document reservada = cola().findOneAndUpdate(
                    and(eq("id_evento", idEvento), eq("habilitada", true), hayLugar),
                    new Document("$inc", new Document("activos", 1L).append("en_espera", -1L)),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            if (reservada == null) {
                return;
            }
            Date ahora = new Date();
            Document admitido = turnos().findOneAndUpdate(
                    and(eq("id_evento", idEvento), eq("estado", "ESPERANDO")),
                    combine(set("estado", "ADMITIDO"), set("admitido_en", ahora), set("expira_en", expiracion(reservada))),
                    new FindOneAndUpdateOptions().sort(Sorts.ascending("turno")).returnDocument(ReturnDocument.AFTER));
            if (admitido == null) {
                // el contador cuenta a alguien cuyo documento aún no se insertó: se devuelve el lugar
                cola().updateOne(eq("id_evento", idEvento), combine(inc("activos", -1L), inc("en_espera", 1L)));
                if (++devoluciones >= MAXIMO_DEVOLUCIONES_SEGUIDAS) {
                    return;
                }
                continue;
            }
            devoluciones = 0;
            cola().updateOne(eq("id_evento", idEvento), Updates.max("ultimo_admitido", numero(admitido, "turno")));
        }
    }

    /** Suma {@code delta} a un contador sin dejarlo bajar de 0 (una caída entre dos pasos no debe romper la cola). */
    private void ajustar(Integer idEvento, String campo, long delta) {
        cola().updateOne(eq("id_evento", idEvento), List.of(new Document("$set", new Document(campo,
                new Document("$max", List.of(0L, new Document("$add", List.of("$" + campo, delta))))))));
    }

    // --------------------------------------------------------------------------- apoyo

    private static Bson activo(Integer idEvento, Integer idUsuario) {
        return and(eq("id_evento", idEvento), eq("id_usuario", idUsuario), eq("activo", true));
    }

    private Document turnoActivo(Integer idEvento, Integer idUsuario) {
        return turnos().find(activo(idEvento, idUsuario)).first();
    }

    private static Date expiracion(Document cola) {
        return Date.from(Instant.now().plus(cola.getInteger("ttl_minutos"), ChronoUnit.MINUTES));
    }

    private static long numero(Document d, String campo) {
        return ((Number) d.get(campo)).longValue();
    }

    private TurnoDto aDto(Integer idEvento, Document t) {
        String estado = t.getString("estado");
        long turno = numero(t, "turno");
        long posicion = 0;
        if ("ESPERANDO".equals(estado)) {
            Document c = cola().find(eq("id_evento", idEvento)).first();
            posicion = Math.max(1, turno - numero(c, "ultimo_admitido"));
        }
        Date expira = t.getDate("expira_en");
        return new TurnoDto(idEvento, turno, estado, posicion,
                "ADMITIDO".equals(estado) && expira != null ? expira.toInstant().atOffset(ZoneOffset.UTC) : null);
    }
}
