package co.soundpass.mongo;

import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Crea (de forma idempotente) las colecciones con su validador $jsonSchema y sus índices.
 * PostgreSQL es la fuente de verdad: si MongoDB no responde al arrancar, la aplicación arranca igual y
 * el esquema se prepara en la primera escritura que llegue cuando MongoDB vuelva.
 */
@Component
public class MongoEsquema implements ApplicationRunner {

    public static final String RESENAS = "reseñas";
    public static final String PERFILES_ARTISTA = "perfiles_artista";
    public static final String DETALLE_EVENTO = "detalle_evento";
    public static final int VERSION = 1;

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
            asegurar();
            listo = true;
        }
    }

    public synchronized void asegurar() {
        validador(RESENAS, esquemaResenas());
        validador(PERFILES_ARTISTA, esquemaPerfilesArtista());
        validador(DETALLE_EVENTO, esquemaDetalleEvento());

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