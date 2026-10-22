package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.soporte.PruebaApiBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/**
 * Misma aplicación, pero con una URI de MongoDB a la que nadie responde: PostgreSQL es la fuente de verdad,
 * así que el catálogo y la compra deben seguir funcionando.
 */
@SpringBootTest(properties = "spring.data.mongodb.uri=mongodb://soundpass:x@127.0.0.1:1/soundpass"
        + "?authSource=admin&serverSelectionTimeoutMS=500&connectTimeoutMS=500")
class MongoCaidoTest extends PruebaApiBase {

    @Test
    void elCatalogoYLaCompraFuncionanSinMongo() throws Exception {
        int evento = eventoPublicado().get("idEvento").asInt();
        int tipo = crearTipoEntrada(evento, "General", "100.00", 5).get("idTipoEntrada").asInt();
        Sesion ana = nuevaSesion();

        mvc.perform(get("/api/v1/eventos")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/v1/eventos/" + evento)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/artistas")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/" + evento + "/disponibilidad")).andExpect(status().isOk());
        comprar(ana.bearer(), "clave-1", linea(tipo, 2)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/ordenes/mias").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void lasFuncionesQueUsanMongoRespondenConUn503EstandarOUn403SinTocarMongo() throws Exception {
        int evento = eventoPublicado().get("idEvento").asInt();
        int tipo = crearTipoEntrada(evento, "General", "100.00", 5).get("idTipoEntrada").asInt();
        int artista = crearArtista("rock").get("idArtista").asInt();
        Sesion ana = nuevaSesion();
        comprarOk(ana.bearer(), linea(tipo, 1));

        mvc.perform(get("/api/v1/eventos/" + evento + "/detalle")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.estado").value(503));
        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/eventos/" + evento + "/resenas/resumen")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/artistas/" + artista + "/perfil")).andExpect(status().isServiceUnavailable());
        mvc.perform(put("/api/v1/eventos/" + evento + "/detalle").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("descripcion", "x"))))
                .andExpect(status().isServiceUnavailable());

        // la regla 6 se valida en PostgreSQL antes de llegar a MongoDB: sin entrada USADA es 403, no 503
        mvc.perform(post("/api/v1/eventos/" + evento + "/resenas").header("Authorization", ana.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("calificacion", 5))))
                .andExpect(status().isForbidden());
        jdbc.update("update entrada set estado = 'USADA' where id_orden in "
                + "(select id_orden from orden where id_usuario = ?)", ana.usuario().getId());
        mvc.perform(post("/api/v1/eventos/" + evento + "/resenas").header("Authorization", ana.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("calificacion", 5))))
                .andExpect(status().isServiceUnavailable());
        assertThat(contar("select count(*) from orden")).isEqualTo(1);
    }
}
