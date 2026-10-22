package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.mongo.MongoEsquema;
import co.soundpass.soporte.PruebaApiBase;
import com.mongodb.MongoWriteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PerfilesDetalleTest extends PruebaApiBase {

    @Autowired MongoEsquema esquema;

    private ResultActions guardarPerfil(String bearer, int idArtista, Object cuerpo) throws Exception {
        var peticion = put("/api/v1/artistas/" + idArtista + "/perfil").contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo));
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    private ResultActions guardarDetalle(String bearer, int idEvento, Object cuerpo) throws Exception {
        var peticion = put("/api/v1/eventos/" + idEvento + "/detalle").contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo));
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    private static Map<String, Object> perfil(String biografia, List<String> galeria) {
        Map<String, Object> p = new HashMap<>();
        p.put("biografia", biografia);
        p.put("redes", Map.of("instagram", "@banda", "x", "@banda_x"));
        p.put("enlaces", List.of("https://banda.example/web"));
        p.put("galeria", galeria);
        return p;
    }

    private long documentos(String coleccion) {
        return mongo.getCollection(coleccion).countDocuments();
    }

    // ------------------------------------------------------------------ perfiles

    @Test
    void creaLeeYActualizaElPerfilDeUnArtista() throws Exception {
        int id = crearArtista("rock").get("idArtista").asInt();

        guardarPerfil(admin, id, perfil("Banda de rock", List.of("artistas/1/foto1.jpg", "artistas/1/foto2.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idArtista").value(id))
                .andExpect(jsonPath("$.biografia").value("Banda de rock"))
                .andExpect(jsonPath("$.galeria.length()").value(2));
        mvc.perform(get("/api/v1/artistas/" + id + "/perfil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redes.instagram").value("@banda"))
                .andExpect(jsonPath("$.enlaces[0]").value("https://banda.example/web"))
                .andExpect(jsonPath("$.galeria[1]").value("artistas/1/foto2.jpg"));

        guardarPerfil(admin, id, perfil("Nueva biografía", List.of("artistas/1/nueva.jpg"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/artistas/" + id + "/perfil"))
                .andExpect(jsonPath("$.biografia").value("Nueva biografía"))
                .andExpect(jsonPath("$.galeria.length()").value(1));
        assertThat(documentos(MongoEsquema.PERFILES_ARTISTA)).isEqualTo(1);
        Document guardado = mongo.getCollection(MongoEsquema.PERFILES_ARTISTA).find().first();
        assertThat(guardado.getInteger("id_artista")).isEqualTo(id);
        assertThat(guardado.getInteger("version_esquema")).isEqualTo(1);
        assertThat(guardado.getList("galeria", String.class)).containsExactly("artistas/1/nueva.jpg");
    }

    @Test
    void unPerfilPuedeTenerSoloAlgunosCampos() throws Exception {
        int id = crearArtista("rock").get("idArtista").asInt();

        guardarPerfil(admin, id, Map.of("biografia", "Solo texto")).andExpect(status().isOk())
                .andExpect(jsonPath("$.galeria").isEmpty())
                .andExpect(jsonPath("$.redes").isEmpty());
        mvc.perform(get("/api/v1/artistas/" + id + "/perfil")).andExpect(jsonPath("$.biografia").value("Solo texto"));
    }

    @Test
    void sinPerfilElCatalogoResponde() throws Exception {
        int id = crearArtista("rock").get("idArtista").asInt();

        mvc.perform(get("/api/v1/artistas/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/artistas")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/artistas/" + id + "/perfil")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/artistas/999999/perfil")).andExpect(status().isNotFound());
    }

    @Test
    void soloAdminEscribeElPerfilYElArtistaDebeExistir() throws Exception {
        int id = crearArtista("rock").get("idArtista").asInt();

        guardarPerfil(null, id, perfil("x", List.of())).andExpect(status().isUnauthorized());
        guardarPerfil(usuario, id, perfil("x", List.of())).andExpect(status().isForbidden());
        guardarPerfil(admin, 999999, perfil("x", List.of())).andExpect(status().isNotFound());
        assertThat(documentos(MongoEsquema.PERFILES_ARTISTA)).isZero();
    }

    @Test
    void laGaleriaSoloAdmiteRutasRelativas() throws Exception {
        int id = crearArtista("rock").get("idArtista").asInt();

        for (String ruta : List.of("/etc/passwd", "https://sitio.example/foto.jpg", "../fuera.jpg",
                "carpeta/../../fuera.jpg", "C:\\fotos\\a.jpg", "\\\\servidor\\a.jpg")) {
            guardarPerfil(admin, id, perfil("x", List.of(ruta))).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        guardarPerfil(admin, id, perfil("x", List.of("a".repeat(256)))).andExpect(status().isUnprocessableEntity());
        Map<String, Object> redInvalida = perfil("x", List.of());
        redInvalida.put("redes", Map.of("red.social", "@banda"));
        guardarPerfil(admin, id, redInvalida).andExpect(status().isUnprocessableEntity());
        assertThat(documentos(MongoEsquema.PERFILES_ARTISTA)).isZero();
    }

    @Test
    void mongoGarantizaUnPerfilPorArtistaYValidaElEsquema() {
        esquema.asegurar();
        var coleccion = mongo.getCollection(MongoEsquema.PERFILES_ARTISTA);
        coleccion.insertOne(new Document("id_artista", 1).append("biografia", "a").append("version_esquema", 1));

        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_artista", 1).append("version_esquema", 1)))
                .isInstanceOf(MongoWriteException.class)
                .satisfies(e -> assertThat(((MongoWriteException) e).getCode()).isEqualTo(11000));
        assertThatThrownBy(() -> coleccion.insertOne(new Document("biografia", "sin id").append("version_esquema", 1)))
                .isInstanceOf(MongoWriteException.class)
                .satisfies(e -> assertThat(((MongoWriteException) e).getCode()).isEqualTo(121));
        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_artista", 2).append("version_esquema", 1)
                .append("galeria", List.of(new byte[] {1, 2}))))
                .isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_artista", 3).append("version_esquema", 1)
                .append("galeria", List.of("http://x/y.jpg"))))
                .isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_artista", "tres").append("version_esquema", 1)))
                .isInstanceOf(MongoWriteException.class);
        assertThat(coleccion.countDocuments()).isEqualTo(1);
    }

    // ------------------------------------------------------------------- detalle

    @Test
    void creaLeeYActualizaElDetalleDeUnEvento() throws Exception {
        int id = eventoPublicado().get("idEvento").asInt();
        Map<String, Object> detalle = Map.of("descripcion", "Gran noche", "etiquetas", List.of("rock", "en vivo"),
                "politicas", "Mayores de 18", "medios", List.of("eventos/5/cartel.jpg"));

        guardarDetalle(admin, id, detalle).andExpect(status().isOk())
                .andExpect(jsonPath("$.idEvento").value(id))
                .andExpect(jsonPath("$.etiquetas.length()").value(2));
        mvc.perform(get("/api/v1/eventos/" + id + "/detalle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Gran noche"))
                .andExpect(jsonPath("$.politicas").value("Mayores de 18"))
                .andExpect(jsonPath("$.medios[0]").value("eventos/5/cartel.jpg"));

        guardarDetalle(admin, id, Map.of("descripcion", "Cambiada")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/" + id + "/detalle"))
                .andExpect(jsonPath("$.descripcion").value("Cambiada"))
                .andExpect(jsonPath("$.etiquetas").isEmpty());
        assertThat(documentos(MongoEsquema.DETALLE_EVENTO)).isEqualTo(1);
        assertThat(mongo.getCollection(MongoEsquema.DETALLE_EVENTO).find().first().getInteger("id_evento"))
                .isEqualTo(id);
    }

    @Test
    void sinDetalleElCatalogoDeEventosResponde() throws Exception {
        int id = eventoPublicado().get("idEvento").asInt();

        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/" + id + "/detalle")).andExpect(status().isNotFound());
    }

    @Test
    void soloAdminEscribeElDetalleYElEventoDebeExistir() throws Exception {
        int id = eventoPublicado().get("idEvento").asInt();

        guardarDetalle(null, id, Map.of("descripcion", "x")).andExpect(status().isUnauthorized());
        guardarDetalle(usuario, id, Map.of("descripcion", "x")).andExpect(status().isForbidden());
        guardarDetalle(admin, 999999, Map.of("descripcion", "x")).andExpect(status().isNotFound());
        assertThat(documentos(MongoEsquema.DETALLE_EVENTO)).isZero();
    }

    @Test
    void elDetalleDeUnEventoNoPublicadoSoloLoVeUnAdmin() throws Exception {
        int escenario = crearEscenario(unico("Ciudad")).get("idEscenario").asInt();
        int borrador = crearEvento(escenario, 0, 3).get("idEvento").asInt();
        guardarDetalle(admin, borrador, Map.of("descripcion", "Borrador")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/eventos/" + borrador + "/detalle")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/eventos/" + borrador + "/detalle").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void validaElDetalleAntesDeGuardar() throws Exception {
        int id = eventoPublicado().get("idEvento").asInt();

        for (Map<String, Object> invalido : List.<Map<String, Object>>of(
                Map.of("medios", List.of("/absoluta.jpg")),
                Map.of("medios", List.of("http://sitio.example/a.jpg")),
                Map.of("etiquetas", List.of(" ")),
                Map.of("etiquetas", List.of("x".repeat(51))),
                Map.of("descripcion", "d".repeat(5001)))) {
            guardarDetalle(admin, id, invalido).andExpect(status().isUnprocessableEntity());
        }
        assertThat(documentos(MongoEsquema.DETALLE_EVENTO)).isZero();
    }

    @Test
    void mongoGarantizaUnDetallePorEventoYIndexaLasEtiquetas() {
        esquema.asegurar();
        var coleccion = mongo.getCollection(MongoEsquema.DETALLE_EVENTO);
        coleccion.insertOne(new Document("id_evento", 1).append("etiquetas", List.of("rock")).append("version_esquema", 1));

        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_evento", 1).append("version_esquema", 1)))
                .isInstanceOf(MongoWriteException.class)
                .satisfies(e -> assertThat(((MongoWriteException) e).getCode()).isEqualTo(11000));
        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_evento", 2).append("version_esquema", 1)
                .append("etiquetas", "no-es-lista")))
                .isInstanceOf(MongoWriteException.class);
        assertThatThrownBy(() -> coleccion.insertOne(new Document("id_evento", 3)))
                .isInstanceOf(MongoWriteException.class);
        var indices = mongo.indexOps(MongoEsquema.DETALLE_EVENTO).getIndexInfo();
        assertThat(indices).extracting(i -> i.getName()).contains("ux_detalle_evento", "ix_detalle_etiquetas");
        assertThat(indices.stream().filter(i -> i.getName().equals("ux_detalle_evento")).findFirst().orElseThrow()
                .isUnique()).isTrue();
    }
}
