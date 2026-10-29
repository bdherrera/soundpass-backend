package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.config.MongoEsquema;
import co.soundpass.soporte.PruebaApiBase;
import com.mongodb.MongoWriteException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ResenasTest extends PruebaApiBase {

    @Autowired MongoEsquema esquema;

    private int evento;
    private int tipo;

    @BeforeEach
    void preparar() throws Exception {
        evento = eventoPublicado().get("idEvento").asInt();
        tipo = crearTipoEntrada(evento, "General", "10.00", 20).get("idTipoEntrada").asInt();
    }

    private long resenasGuardadas() {
        return mongo.getCollection(MongoEsquema.RESENAS).countDocuments();
    }

    /** Usuario que compró una entrada del evento y ya la usó. */
    private Sesion asistente(int idTipo) throws Exception {
        Sesion s = nuevaSesion();
        comprarOk(s.bearer(), linea(idTipo, 1));
        jdbc.update("update entrada set estado = 'USADA' where id_orden in "
                + "(select id_orden from orden where id_usuario = ?)", s.usuario().getId());
        return s;
    }

    private ResultActions resenar(String bearer, int idEvento, Object calificacion, String comentario)
            throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("calificacion", calificacion);
        cuerpo.put("comentario", comentario);
        var peticion = post("/api/v1/eventos/" + idEvento + "/resenas").contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo));
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    @Test
    void sinEntradaUsadaNoSePuedeReseñar() throws Exception {
        Sesion sinEntrada = nuevaSesion();
        Sesion soloVendida = nuevaSesion();
        comprarOk(soloVendida.bearer(), linea(tipo, 1));
        Sesion cancelada = nuevaSesion();
        int idOrden = comprarOk(cancelada.bearer(), linea(tipo, 1)).get("idOrden").asInt();
        mvc.perform(post("/api/v1/ordenes/" + idOrden + "/cancelacion").header("Authorization", cancelada.bearer()))
                .andExpect(status().isOk());

        for (Sesion s : List.of(sinEntrada, soloVendida, cancelada)) {
            resenar(s.bearer(), evento, 5, "Genial").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }
        assertThat(resenasGuardadas()).isZero();
    }

    @Test
    void conUnaEntradaUsadaSeCreaLaReseña() throws Exception {
        Sesion ana = asistente(tipo);

        resenar(ana.bearer(), evento, 4, "  Muy buen concierto  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.idEvento").value(evento))
                .andExpect(jsonPath("$.idUsuario").value(ana.usuario().getId()))
                .andExpect(jsonPath("$.nombreUsuario").value(ana.usuario().getNombre()))
                .andExpect(jsonPath("$.calificacion").value(4))
                .andExpect(jsonPath("$.comentario").value("Muy buen concierto"))
                .andExpect(jsonPath("$.fecha").isNotEmpty());

        Document guardado = mongo.getCollection(MongoEsquema.RESENAS).find().first();
        assertThat(guardado.getInteger("id_evento")).isEqualTo(evento);
        assertThat(guardado.getInteger("version_esquema")).isEqualTo(1);
        assertThat(guardado.get("usuario", Document.class).getString("nombre")).isEqualTo(ana.usuario().getNombre());
        assertThat(guardado.get("fecha")).isInstanceOf(Date.class);
    }

    @Test
    void laEntradaUsadaDeOtroEventoNoSirve() throws Exception {
        Sesion ana = asistente(tipo);
        int otroEvento = eventoPublicado().get("idEvento").asInt();

        resenar(ana.bearer(), otroEvento, 5, null).andExpect(status().isForbidden());
        resenar(ana.bearer(), 999999, 5, null).andExpect(status().isNotFound());
        assertThat(resenasGuardadas()).isZero();
    }

    @Test
    void laSegundaReseñaDelMismoUsuarioSeRechaza() throws Exception {
        Sesion ana = asistente(tipo);
        resenar(ana.bearer(), evento, 5, "Primera").andExpect(status().isCreated());

        resenar(ana.bearer(), evento, 1, "Segunda").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        assertThat(resenasGuardadas()).isEqualTo(1);
        assertThat(mongo.getCollection(MongoEsquema.RESENAS).find().first().getString("comentario"))
                .isEqualTo("Primera");
    }

    @Test
    void variasSolicitudesSimultaneasDelMismoUsuarioCreanUnaSolaReseña() throws Exception {
        Sesion ana = asistente(tipo);

        List<Respuesta> respuestas = enParalelo(6, i -> () -> respuesta(resenar(ana.bearer(), evento, 5, "x")));

        assertThat(respuestas.stream().filter(r -> r.estado() == 201)).hasSize(1);
        assertThat(respuestas.stream().filter(r -> r.estado() == 409)).hasSize(5);
        assertThat(resenasGuardadas()).isEqualTo(1);
    }

    @Test
    void laCalificacionDebeEstarEntre1Y5() throws Exception {
        Sesion ana = asistente(tipo);

        for (Object invalida : new Object[] {0, 6, -1, null}) {
            resenar(ana.bearer(), evento, invalida, null).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        resenar(ana.bearer(), evento, 3, "x".repeat(2001)).andExpect(status().isUnprocessableEntity());
        assertThat(resenasGuardadas()).isZero();
        resenar(null, evento, 5, null).andExpect(status().isUnauthorized());
    }

    @Test
    void elValidadorDeMongoRechazaDocumentosInvalidos() {
        esquema.asegurar();
        var coleccion = mongo.getCollection(MongoEsquema.RESENAS);
        Document valido = documento(5);
        coleccion.insertOne(valido);
        assertThat(coleccion.countDocuments()).isEqualTo(1);

        assertThatThrownBy(() -> coleccion.insertOne(documento(7))).isInstanceOf(MongoWriteException.class)
                .satisfies(e -> assertThat(((MongoWriteException) e).getCode()).isEqualTo(121));
        assertThatThrownBy(() -> coleccion.insertOne(documento(0))).isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> coleccion.insertOne(documento(4.5))).isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> coleccion.insertOne(documento(3).append("id_evento", "texto")))
                .isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> coleccion.insertOne(documento(3).append("id_usuario", 2).append("usuario", null)))
                .isInstanceOf(MongoWriteException.class);
        Document sinFecha = documento(3).append("id_usuario", 3);
        sinFecha.remove("fecha");
        assertThatThrownBy(() -> coleccion.insertOne(sinFecha)).isInstanceOf(MongoWriteException.class);
        assertThat(coleccion.countDocuments()).isEqualTo(1);
    }

    private static Document documento(Number calificacion) {
        return new Document("id_evento", 1).append("id_usuario", 1)
                .append("usuario", new Document("nombre", "Ana"))
                .append("calificacion", calificacion)
                .append("comentario", null)
                .append("fecha", new Date())
                .append("version_esquema", 1);
    }

    @Test
    void listaLasReseñasDelEventoDeLaMasNuevaALaMasAntigua() throws Exception {
        for (int calificacion : new int[] {5, 4, 3}) {
            resenar(asistente(tipo).bearer(), evento, calificacion, "c" + calificacion).andExpect(status().isCreated());
            Thread.sleep(5);
        }

        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[0].comentario").value("c3"))
                .andExpect(jsonPath("$.contenido[2].comentario").value("c5"));
        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas").param("tamano", "2"))
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas").param("tamano", "2").param("pagina", "1"))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].comentario").value("c5"));
        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas").param("tamano", "0"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/eventos/999999/resenas")).andExpect(status().isNotFound());
    }

    @Test
    void calculaPromedioYConteo() throws Exception {
        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promedio").value(0.0))
                .andExpect(jsonPath("$.total").value(0));

        for (int calificacion : new int[] {5, 4, 4}) {
            resenar(asistente(tipo).bearer(), evento, calificacion, null).andExpect(status().isCreated());
        }

        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas/resumen"))
                .andExpect(jsonPath("$.idEvento").value(evento))
                .andExpect(jsonPath("$.promedio").value(4.33))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v1/eventos/999999/resenas/resumen")).andExpect(status().isNotFound());
    }

    @Test
    void lasReseñasDeUnEventoNoPublicadoNoSonPublicas() throws Exception {
        int escenario = crearEscenario(unico("Ciudad")).get("idEscenario").asInt();
        int borrador = crearEvento(escenario, 0, 3).get("idEvento").asInt();

        mvc.perform(get("/api/v1/eventos/" + borrador + "/resenas")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/eventos/" + borrador + "/resenas/resumen")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/eventos/" + borrador + "/resenas").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void creaLasColeccionesConValidadorEIndices() {
        esquema.asegurar();
        esquema.asegurar(); // idempotente

        for (String nombre : List.of(MongoEsquema.RESENAS, MongoEsquema.PERFILES_ARTISTA, MongoEsquema.DETALLE_EVENTO)) {
            Document info = mongo.getDb().runCommand(new Document("listCollections", 1)
                    .append("filter", new Document("name", nombre)));
            Document opciones = info.get("cursor", Document.class).getList("firstBatch", Document.class).get(0)
                    .get("options", Document.class);
            assertThat(opciones.get("validator", Document.class).get("$jsonSchema")).isNotNull();
            assertThat(opciones.getString("validationAction")).isEqualTo("error");
        }
        var indices = mongo.indexOps(MongoEsquema.RESENAS).getIndexInfo();
        var unico = indices.stream().filter(i -> i.getName().equals("ux_resena_evento_usuario")).findFirst().orElseThrow();
        assertThat(unico.isUnique()).isTrue();
        assertThat(unico.getIndexFields()).extracting(f -> f.getKey()).containsExactly("id_evento", "id_usuario");
        var porFecha = indices.stream().filter(i -> i.getName().equals("ix_resena_evento_fecha")).findFirst().orElseThrow();
        assertThat(porFecha.getIndexFields().get(1).getDirection().isDescending()).isTrue();
    }
}
