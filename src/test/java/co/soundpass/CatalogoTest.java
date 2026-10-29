package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CatalogoTest extends PruebaApiBase {

    // ---------------------------------------------------------------- artistas

    @Test
    void adminCreaUnArtistaYCualquieraLoLee() throws Exception {
        JsonNode creado = crearArtista("rock");
        int id = creado.get("idArtista").asInt();

        mvc.perform(get("/api/v1/artistas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreArtistico").value(creado.get("nombreArtistico").asText()))
                .andExpect(jsonPath("$.genero").value("rock"));
    }

    @Test
    void artistaInvalidoDevuelve422ConDetalle() throws Exception {
        mvc.perform(post("/api/v1/artistas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreArtistico", "  "))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("nombreArtistico"));
    }

    @Test
    void soloAdminEscribeArtistas() throws Exception {
        JsonNode artista = crearArtista("rock");
        String ruta = "/api/v1/artistas/" + artista.get("idArtista").asInt();
        String cuerpo = json(Map.of("nombreArtistico", "Otro"));

        mvc.perform(post("/api/v1/artistas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/artistas").header("Authorization", usuario)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(put(ruta).header("Authorization", usuario).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ruta).header("Authorization", usuario)).andExpect(status().isForbidden());
        mvc.perform(delete(ruta)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminActualizaUnArtista() throws Exception {
        int id = crearArtista("rock").get("idArtista").asInt();

        mvc.perform(put("/api/v1/artistas/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreArtistico", "Nuevo nombre", "pais", "Chile"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreArtistico").value("Nuevo nombre"))
                .andExpect(jsonPath("$.pais").value("Chile"));
        mvc.perform(put("/api/v1/artistas/999999").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nombreArtistico", "X"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listadoDeArtistasEsPublicoPaginadoYFiltrable() throws Exception {
        String genero = unico("genero");
        for (int i = 0; i < 3; i++) {
            crearArtista(genero);
        }
        crearArtista("otro-genero");

        mvc.perform(get("/api/v1/artistas").param("genero", genero).param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.pagina").value(0));
        mvc.perform(get("/api/v1/artistas").param("genero", genero).param("tamano", "2").param("pagina", "1"))
                .andExpect(jsonPath("$.contenido.length()").value(1));
        mvc.perform(get("/api/v1/artistas"))
                .andExpect(jsonPath("$.totalElementos").value(4));
    }

    @Test
    void borrarUnArtistaEsLogicoYDejaDeVerseEnLosListados() throws Exception {
        String genero = unico("genero");
        int id = crearArtista(genero).get("idArtista").asInt();
        String ruta = "/api/v1/artistas/" + id;

        mvc.perform(delete(ruta).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(delete(ruta).header("Authorization", admin)).andExpect(status().isNoContent());

        mvc.perform(get(ruta)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/artistas").param("genero", genero)).andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(put(ruta).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nombreArtistico", "X")))).andExpect(status().isNotFound());
        Boolean activo = jdbc.queryForObject("select activo from artista where id_artista = ?", Boolean.class, id);
        assertThat(activo).isFalse();
        mvc.perform(delete("/api/v1/artistas/999999").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void laPaginacionInvalidaDevuelve422() throws Exception {
        mvc.perform(get("/api/v1/artistas").param("tamano", "0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/artistas").param("tamano", "101")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/escenarios").param("pagina", "-1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    // -------------------------------------------------------------- escenarios

    @Test
    void adminCreaUnEscenarioYCualquieraLoLee() throws Exception {
        JsonNode creado = crearEscenario("Medellín");
        int id = creado.get("idEscenario").asInt();

        mvc.perform(get("/api/v1/escenarios/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad").value("Medellín"))
                .andExpect(jsonPath("$.capacidad").value(5000));
    }

    @Test
    void laCapacidadDebeSerMayorQueCero() throws Exception {
        for (Object capacidad : new Object[] {0, -5}) {
            mvc.perform(post("/api/v1/escenarios").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nombre", "Sala", "ciudad", "Cali", "capacidad", capacidad))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detalles[0].campo").value("capacidad"));
        }
        mvc.perform(post("/api/v1/escenarios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Sala", "ciudad", "Cali"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void soloAdminEscribeEscenarios() throws Exception {
        int id = crearEscenario("Cali").get("idEscenario").asInt();
        String ruta = "/api/v1/escenarios/" + id;
        String cuerpo = json(Map.of("nombre", "Sala", "ciudad", "Cali", "capacidad", 10));

        mvc.perform(post("/api/v1/escenarios").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/escenarios").header("Authorization", usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(put(ruta).header("Authorization", usuario).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ruta).header("Authorization", usuario)).andExpect(status().isForbidden());
    }

    @Test
    void adminActualizaYDesactivaUnEscenario() throws Exception {
        String ciudad = unico("ciudad");
        int id = crearEscenario(ciudad).get("idEscenario").asInt();
        String ruta = "/api/v1/escenarios/" + id;

        mvc.perform(put(ruta).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Teatro", "ciudad", ciudad, "capacidad", 800))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacidad").value(800));

        mvc.perform(get("/api/v1/escenarios").param("ciudad", ciudad.toUpperCase()))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(delete(ruta).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/escenarios").param("ciudad", ciudad)).andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get(ruta)).andExpect(status().isNotFound());
        Boolean activo = jdbc.queryForObject("select activo from escenario where id_escenario = ?", Boolean.class, id);
        assertThat(activo).isFalse();
    }
}
