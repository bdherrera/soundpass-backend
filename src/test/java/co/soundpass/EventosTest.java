package co.soundpass;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class EventosTest extends PruebaApiBase {

    private int escenario() throws Exception {
        return crearEscenario(unico("Ciudad")).get("idEscenario").asInt();
    }

    @Test
    void unEventoNuevoQuedaEnBorradorYSoloLoVeUnAdmin() throws Exception {
        JsonNode evento = crearEvento(escenario(), 0, 3);
        int id = evento.get("idEvento").asInt();

        org.assertj.core.api.Assertions.assertThat(evento.get("estado").asText()).isEqualTo("BORRADOR");
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/eventos/" + id).header("Authorization", usuario)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/eventos/" + id).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos")).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void publicarUnEventoLoHaceVisibleConSuDetalle() throws Exception {
        int idEscenario = escenario();
        int id = crearEvento(idEscenario, 0, 3).get("idEvento").asInt();
        int idArtista = crearArtista("rock").get("idArtista").asInt();
        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/" + idArtista).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ordenPresentacion", 2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ordenPresentacion").value(2));
        crearTipoEntrada(id, "General", "100.00", 3);
        publicar(id);

        mvc.perform(get("/api/v1/eventos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.escenario.idEscenario").value(idEscenario))
                .andExpect(jsonPath("$.artistas[0].idArtista").value(idArtista))
                .andExpect(jsonPath("$.tiposEntrada[0].nombre").value("General"));
        mvc.perform(get("/api/v1/eventos")).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void unEventoSolapadoEnElEscenarioDevuelve409() throws Exception {
        int idEscenario = escenario();
        crearEvento(idEscenario, 0, 4);

        mvc.perform(post("/api/v1/eventos").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(datosEvento(idEscenario, 2, 6))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.codigo").value("SOLAPAMIENTO_ESCENARIO"))
                .andExpect(jsonPath("$.ruta").value("/api/v1/eventos"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

    @Test
    void dosEventosAdyacentesNoSeSolapan() throws Exception {
        int idEscenario = escenario();
        crearEvento(idEscenario, 0, 4);
        crearEvento(idEscenario, 4, 8);
    }

    @Test
    void unEventoCanceladoNoCuentaParaElSolapamiento() throws Exception {
        int idEscenario = escenario();
        int primero = crearEvento(idEscenario, 0, 4).get("idEvento").asInt();
        cambiarEstado(primero, "CANCELADO").andExpect(status().isOk());

        crearEvento(idEscenario, 1, 3);
    }

    @Test
    void moverUnEventoSobreOtroDevuelve409() throws Exception {
        int idEscenario = escenario();
        crearEvento(idEscenario, 0, 4);
        int segundo = crearEvento(idEscenario, 10, 14).get("idEvento").asInt();

        mvc.perform(put("/api/v1/eventos/" + segundo).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datosEvento(idEscenario, 2, 6))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLAPAMIENTO_ESCENARIO"));
        mvc.perform(put("/api/v1/eventos/" + segundo).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datosEvento(idEscenario, 4, 6))))
                .andExpect(status().isOk());
    }

    @Test
    void reactivarUnEventoCanceladoQueChocaDevuelve409() throws Exception {
        int idEscenario = escenario();
        int primero = crearEvento(idEscenario, 0, 4).get("idEvento").asInt();
        cambiarEstado(primero, "CANCELADO").andExpect(status().isOk());
        crearEvento(idEscenario, 0, 4);

        cambiarEstado(primero, "BORRADOR").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLAPAMIENTO_ESCENARIO"));
    }

    @Test
    void soloSeAceptanLasTransicionesDeEstadoValidas() throws Exception {
        int id = crearEvento(escenario(), 0, 3).get("idEvento").asInt();

        cambiarEstado(id, "FINALIZADO").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        cambiarEstado(id, "BORRADOR").andExpect(status().isConflict());
        cambiarEstado(id, "PUBLICADO").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"));
        cambiarEstado(id, "FINALIZADO").andExpect(status().isOk());
        cambiarEstado(id, "PUBLICADO").andExpect(status().isConflict());
        cambiarEstado(id, "CANCELADO").andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/eventos/" + id + "/estado")
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INEXISTENTE\"}"))
                .andExpect(status().isUnprocessableEntity());
        cambiarEstado(999999, "PUBLICADO").andExpect(status().isNotFound());
    }

    @Test
    void validaElEventoAntesDeGuardar() throws Exception {
        int idEscenario = escenario();
        Map<String, Object> alReves = new HashMap<>(datosEvento(idEscenario, 5, 3));
        Map<String, Object> igual = new HashMap<>(datosEvento(idEscenario, 3, 3));
        Map<String, Object> sinNombre = new HashMap<>(datosEvento(idEscenario, 0, 3));
        sinNombre.put("nombre", "");

        for (Map<String, Object> invalido : java.util.List.of(alReves, igual, sinNombre)) {
            mvc.perform(post("/api/v1/eventos").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON).content(json(invalido)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        mvc.perform(post("/api/v1/eventos").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(datosEvento(999999, 0, 3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeProgramarEnUnEscenarioDesactivado() throws Exception {
        int idEscenario = escenario();
        mvc.perform(delete("/api/v1/escenarios/" + idEscenario).header("Authorization", admin))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/eventos").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(datosEvento(idEscenario, 0, 3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloAdminEscribeEventos() throws Exception {
        int idEscenario = escenario();
        int id = crearEvento(idEscenario, 0, 3).get("idEvento").asInt();
        String cuerpo = json(datosEvento(idEscenario, 10, 12));

        mvc.perform(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/eventos").header("Authorization", usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/eventos/" + id).header("Authorization", usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/eventos/" + id + "/estado")
                        .header("Authorization", usuario).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PUBLICADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listaPublicaConFiltrosDeFechaCiudadYArtista() throws Exception {
        String ciudadA = unico("ciudadA");
        String ciudadB = unico("ciudadB");
        int escenarioA = crearEscenario(ciudadA).get("idEscenario").asInt();
        int escenarioB = crearEscenario(ciudadB).get("idEscenario").asInt();
        int cercano = crearEvento(escenarioA, 24, 27).get("idEvento").asInt();
        int lejano = crearEvento(escenarioB, 24 * 10, 24 * 10 + 3).get("idEvento").asInt();
        crearEvento(escenarioA, 24 * 20, 24 * 20 + 3); // queda en BORRADOR
        publicar(cercano);
        publicar(lejano);
        int artista = crearArtista("jazz").get("idArtista").asInt();
        mvc.perform(put("/api/v1/eventos/" + lejano + "/artistas/" + artista).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/eventos")).andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].idEvento").value(cercano));
        mvc.perform(get("/api/v1/eventos").param("ciudad", ciudadA.toUpperCase()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idEvento").value(cercano));
        mvc.perform(get("/api/v1/eventos").param("idArtista", String.valueOf(artista)))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idEvento").value(lejano));
        mvc.perform(get("/api/v1/eventos").param("desde", base.plusDays(5).toString()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idEvento").value(lejano));
        mvc.perform(get("/api/v1/eventos").param("hasta", base.plusDays(5).toString()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idEvento").value(cercano));
        mvc.perform(get("/api/v1/eventos").param("desde", "no-es-fecha")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void asignaYQuitaArtistasDeUnEvento() throws Exception {
        int id = crearEvento(escenario(), 0, 3).get("idEvento").asInt();
        int primero = crearArtista("rock").get("idArtista").asInt();
        int segundo = crearArtista("rock").get("idArtista").asInt();
        publicar(id);

        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/" + segundo).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ordenPresentacion", 2))));
        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/" + primero).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        mvc.perform(get("/api/v1/eventos/" + id))
                .andExpect(jsonPath("$.artistas.length()").value(2))
                .andExpect(jsonPath("$.artistas[0].idArtista").value(primero))
                .andExpect(jsonPath("$.artistas[0].ordenPresentacion").value(1))
                .andExpect(jsonPath("$.artistas[1].idArtista").value(segundo));

        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/" + primero).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ordenPresentacion", 3))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(jsonPath("$.artistas[1].idArtista").value(primero));

        mvc.perform(delete("/api/v1/eventos/" + id + "/artistas/" + primero).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/eventos/" + id + "/artistas/" + primero).header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(jsonPath("$.artistas.length()").value(1));
        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/999999").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/" + primero).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ordenPresentacion", 0))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/eventos/" + id + "/artistas/" + primero).header("Authorization", usuario)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
}
