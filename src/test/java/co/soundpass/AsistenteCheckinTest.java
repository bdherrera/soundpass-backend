package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AsistenteCheckinTest extends PruebaApiBase {

    private int evento;
    private int general;
    private Sesion ana;
    private int idOrden;
    private int idEntrada;
    private String codigo;

    @BeforeEach
    void preparar() throws Exception {
        evento = eventoPublicado().get("idEvento").asInt();
        general = crearTipoEntrada(evento, "General", "100.00", 10).get("idTipoEntrada").asInt();
        ana = nuevaSesion();
        idOrden = comprarOk(ana.bearer(), linea(general, 1)).get("idOrden").asInt();
        JsonNode entrada = cuerpo(entradasMias(ana.bearer())).get("contenido").get(0);
        idEntrada = entrada.get("idEntrada").asInt();
        codigo = entrada.get("codigo").asText();
    }

    private static Map<String, Object> asistente(String nombre, String documento, String correo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombre);
        datos.put("documento", documento);
        datos.put("correo", correo);
        return datos;
    }

    private ResultActions registrar(String bearer, int id, Object datos) throws Exception {
        var peticion = put("/api/v1/entradas/" + id + "/asistente").contentType(MediaType.APPLICATION_JSON)
                .content(json(datos));
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    private ResultActions checkin(String bearer, Object cuerpo) throws Exception {
        var peticion = post("/api/v1/checkins").contentType(MediaType.APPLICATION_JSON).content(json(cuerpo));
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    private ResultActions checkin(String bearer) throws Exception {
        return checkin(bearer, Map.of("codigo", codigo));
    }

    private void registrarAsistente() throws Exception {
        registrar(ana.bearer(), idEntrada, asistente("Luisa", "123", "luisa@prueba.co")).andExpect(status().isCreated());
    }

    private String estadoEntrada() {
        return jdbc.queryForObject("select estado from entrada where id_entrada = ?", String.class, idEntrada);
    }

    // ---------------------------------------------------------------- asistente

    @Test
    void elDuenoRegistraAlAsistenteConLaClaveDeSuEntrada() throws Exception {
        registrar(ana.bearer(), idEntrada, asistente("Luisa", "123", "luisa@prueba.co"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEntrada").value(idEntrada))
                .andExpect(jsonPath("$.nombre").value("Luisa"));

        assertThat(contar("select count(*) from asistente where id_entrada = ?", idEntrada)).isEqualTo(1);
        entradasMias(ana.bearer()).andExpect(jsonPath("$.contenido[0].asistente.documento").value("123"));
    }

    @Test
    void elCorreoDelAsistenteEsOpcional() throws Exception {
        registrar(ana.bearer(), idEntrada, asistente("Luisa", "123", null)).andExpect(status().isCreated());
    }

    @Test
    void unSegundoAsistenteParaLaMismaEntradaSeRechaza() throws Exception {
        registrarAsistente();

        registrar(ana.bearer(), idEntrada, asistente("Otra", "999", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        assertThat(contar("select count(*) from asistente")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select nombre from asistente", String.class)).isEqualTo("Luisa");
    }

    @Test
    void validaLosDatosDelAsistente() throws Exception {
        for (Object invalido : List.of(asistente(" ", "123", null), asistente("Luisa", "", null),
                asistente("Luisa", "1".repeat(31), null), asistente("Luisa", "123", "no-es-correo"))) {
            registrar(ana.bearer(), idEntrada, invalido).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        assertThat(contar("select count(*) from asistente")).isZero();
    }

    @Test
    void soloElDuenoPuedeRegistrarAlAsistente() throws Exception {
        Sesion luis = nuevaSesion();

        registrar(luis.bearer(), idEntrada, asistente("Luisa", "123", null)).andExpect(status().isNotFound());
        registrar(null, idEntrada, asistente("Luisa", "123", null)).andExpect(status().isUnauthorized());
        registrar(ana.bearer(), 999999, asistente("Luisa", "123", null)).andExpect(status().isNotFound());
        assertThat(contar("select count(*) from asistente")).isZero();
    }

    @Test
    void unaEntradaCanceladaNoAdmiteAsistente() throws Exception {
        mvc.perform(post("/api/v1/ordenes/" + idOrden + "/cancelacion").header("Authorization", ana.bearer()))
                .andExpect(status().isOk());

        registrar(ana.bearer(), idEntrada, asistente("Luisa", "123", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    // ------------------------------------------------------------------ check-in

    @Test
    void elCheckinMarcaLaEntradaUsadaYGuardaLaFecha() throws Exception {
        registrarAsistente();

        checkin(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.idEntrada").value(idEntrada))
                .andExpect(jsonPath("$.estado").value("USADA"))
                .andExpect(jsonPath("$.fechaCheckin").isNotEmpty());

        assertThat(estadoEntrada()).isEqualTo("USADA");
        assertThat(contar("select count(*) from asistente where id_entrada = ? and fecha_checkin is not null",
                idEntrada)).isEqualTo(1);
        entradasMias(ana.bearer(), "estado", "USADA").andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].asistente.fechaCheckin").isNotEmpty());
    }

    @Test
    void unCheckinRepetidoSeRechaza() throws Exception {
        registrarAsistente();
        checkin(admin).andExpect(status().isOk());

        checkin(admin).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void soloUnAdminHaceCheckin() throws Exception {
        registrarAsistente();

        checkin(ana.bearer()).andExpect(status().isForbidden());
        checkin(null).andExpect(status().isUnauthorized());
        assertThat(estadoEntrada()).isEqualTo("VENDIDA");
    }

    @Test
    void rechazaCodigosInexistentesOMalFormados() throws Exception {
        checkin(admin, Map.of("codigo", "00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
        checkin(admin, Map.of("codigo", "no-es-uuid")).andExpect(status().isUnprocessableEntity());
        checkin(admin, Map.of()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elCheckinExigeAsistenteRegistrado() throws Exception {
        checkin(admin).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        assertThat(estadoEntrada()).isEqualTo("VENDIDA");
    }

    @Test
    void unaEntradaCanceladaNoAdmiteCheckin() throws Exception {
        registrarAsistente();
        mvc.perform(post("/api/v1/ordenes/" + idOrden + "/cancelacion").header("Authorization", ana.bearer()))
                .andExpect(status().isOk());

        checkin(admin).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void unaEntradaSinVenderNoAdmiteCheckin() throws Exception {
        String sinVender = jdbc.queryForObject("select codigo::text from entrada where estado = 'DISPONIBLE' limit 1",
                String.class);

        checkin(admin, Map.of("codigo", sinVender)).andExpect(status().isConflict());
    }

    @Test
    void unEventoNoPublicadoNoAdmiteCheckin() throws Exception {
        registrarAsistente();
        cambiarEstado(evento, "CANCELADO").andExpect(status().isOk());

        checkin(admin).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        assertThat(estadoEntrada()).isEqualTo("VENDIDA");
    }

    @Test
    void variosCheckinSimultaneosDelMismoCodigoSoloUnoProcede() throws Exception {
        registrarAsistente();

        List<Respuesta> respuestas = enParalelo(6, i -> () -> respuesta(checkin(admin)));

        assertThat(respuestas.stream().filter(r -> r.estado() == 200)).hasSize(1);
        assertThat(respuestas.stream().filter(r -> r.estado() == 409)).hasSize(5);
    }
}
