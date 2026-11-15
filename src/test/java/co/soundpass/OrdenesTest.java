package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class OrdenesTest extends PruebaApiBase {

    private int evento;
    private int general;
    private int vip;
    private Sesion ana;
    private Sesion luis;

    @BeforeEach
    void preparar() throws Exception {
        evento = eventoPublicado().get("idEvento").asInt();
        general = crearTipoEntrada(evento, "General", "100.00", 10).get("idTipoEntrada").asInt();
        vip = crearTipoEntrada(evento, "VIP", "50.00", 5).get("idTipoEntrada").asInt();
        ana = nuevaSesion();
        luis = nuevaSesion();
    }

    private int entradas(int idOrden, String estado) {
        return contar("select count(*) from entrada where id_orden = ? and estado = ?", idOrden, estado);
    }

    /** Cancela la orden; {@code bearer} null significa sin token. */
    private ResultActions cancelar(String bearer, int idOrden) throws Exception {
        var peticion = post("/api/v1/ordenes/" + idOrden + "/cancelacion");
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    @Test
    void misOrdenesSoloMuestraLasPropiasMasNuevasPrimero() throws Exception {
        comprarOk(ana.bearer(), linea(general, 1));
        comprarOk(ana.bearer(), linea(vip, 3));
        comprarOk(luis.bearer(), linea(general, 2));

        mvc.perform(get("/api/v1/ordenes/mias").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].total").value(150.0))
                .andExpect(jsonPath("$.contenido[0].detalles[0].cantidad").value(3))
                .andExpect(jsonPath("$.contenido[1].total").value(100.0));
        mvc.perform(get("/api/v1/ordenes/mias").header("Authorization", ana.bearer()).param("tamano", "1"))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/v1/ordenes/mias").header("Authorization", luis.bearer()))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/v1/ordenes/mias")).andExpect(status().isUnauthorized());
    }

    @Test
    void unaOrdenAjenaNoSeVe() throws Exception {
        int idOrden = comprarOk(ana.bearer(), linea(general, 1)).get("idOrden").asInt();

        mvc.perform(get("/api/v1/ordenes/" + idOrden).header("Authorization", ana.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idOrden").value(idOrden));
        mvc.perform(get("/api/v1/ordenes/" + idOrden).header("Authorization", luis.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ordenes/999999").header("Authorization", ana.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ordenes/" + idOrden)).andExpect(status().isUnauthorized());
    }

    @Test
    void misEntradasMuestraSoloLasPropiasConSuCodigo() throws Exception {
        comprarOk(ana.bearer(), linea(general, 2));
        comprarOk(luis.bearer(), linea(vip, 1));

        entradasMias(ana.bearer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].estado").value("VENDIDA"))
                .andExpect(jsonPath("$.contenido[0].tipoEntrada").value("General"))
                .andExpect(jsonPath("$.contenido[0].idEvento").value(evento))
                .andExpect(jsonPath("$.contenido[0].codigo")
                        .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")));
        entradasMias(ana.bearer(), "estado", "VENDIDA").andExpect(jsonPath("$.totalElementos").value(2));
        entradasMias(ana.bearer(), "estado", "USADA").andExpect(jsonPath("$.totalElementos").value(0));
        entradasMias(ana.bearer(), "estado", "XYZ").andExpect(status().isUnprocessableEntity());
        entradasMias(ana.bearer(), "tamano", "1").andExpect(jsonPath("$.totalPaginas").value(2));
        entradasMias(luis.bearer()).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/v1/entradas/mias")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelarUnaOrdenPagadaLaReembolsaYCancelaSusEntradas() throws Exception {
        JsonNode orden = comprarOk(ana.bearer(), linea(general, 2), linea(vip, 1));
        int idOrden = orden.get("idOrden").asInt();

        cancelar(ana.bearer(), idOrden)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REEMBOLSADA"))
                .andExpect(jsonPath("$.total").value(orden.get("total").decimalValue().doubleValue()))
                .andExpect(jsonPath("$.detalles.length()").value(2));

        assertThat(entradas(idOrden, "CANCELADA")).isEqualTo(3);
        assertThat(entradas(idOrden, "VENDIDA")).isZero();
        // el inventario sigue consistente: ninguna entrada desapareció ni volvió a estar disponible
        assertThat(contar("select count(*) from entrada where id_tipo_entrada = ?", general)).isEqualTo(10);
        assertThat(contar("select count(*) from entrada where id_tipo_entrada = ? and estado = 'DISPONIBLE'", general))
                .isEqualTo(8);
        assertThat(contar("select count(*) from entrada where estado <> 'DISPONIBLE' and id_orden is null")).isZero();
        assertThat(jdbc.queryForObject("select total from v_orden_total where id_orden = ?", BigDecimal.class,
                idOrden)).isEqualByComparingTo("250.00");
        entradasMias(ana.bearer(), "estado", "CANCELADA").andExpect(jsonPath("$.totalElementos").value(3));
        mvc.perform(get("/api/v1/eventos/" + evento + "/disponibilidad"))
                .andExpect(jsonPath("$[0].disponibles").value(8)).andExpect(jsonPath("$[1].disponibles").value(4));
    }

    @Test
    void cancelarUnaOrdenPendienteLaDejaCancelada() throws Exception {
        int idOrden = jdbc.queryForObject("insert into orden (id_usuario, clave_idempotencia, estado) "
                + "values (?, 'pendiente-1', 'PENDIENTE') returning id_orden", Integer.class, ana.usuario().getId());
        jdbc.update("update entrada set estado = 'VENDIDA', id_orden = ? where id_entrada in "
                + "(select id_entrada from entrada where id_tipo_entrada = ? order by id_entrada limit 2)",
                idOrden, general);

        cancelar(ana.bearer(), idOrden).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        assertThat(entradas(idOrden, "CANCELADA")).isEqualTo(2);
    }

    @Test
    void cancelarDosVecesDevuelveElErrorEstandar() throws Exception {
        int idOrden = comprarOk(ana.bearer(), linea(general, 1)).get("idOrden").asInt();
        cancelar(ana.bearer(), idOrden).andExpect(status().isOk());

        cancelar(ana.bearer(), idOrden).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.ruta").value("/api/v1/ordenes/" + idOrden + "/cancelacion"));
        assertThat(entradas(idOrden, "CANCELADA")).isEqualTo(1);
    }

    @Test
    void noSePuedeCancelarUnaOrdenConEntradasUsadas() throws Exception {
        int idOrden = comprarOk(ana.bearer(), linea(general, 2)).get("idOrden").asInt();
        jdbc.update("update entrada set estado = 'USADA' where id_entrada = "
                + "(select min(id_entrada) from entrada where id_orden = ?)", idOrden);

        cancelar(ana.bearer(), idOrden).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        assertThat(entradas(idOrden, "VENDIDA")).isEqualTo(1);
        assertThat(entradas(idOrden, "USADA")).isEqualTo(1);
        assertThat(entradas(idOrden, "CANCELADA")).isZero();
        assertThat(contar("select count(*) from orden where id_orden = ? and estado = 'PAGADA'", idOrden)).isEqualTo(1);
    }

    @Test
    void noSePuedeCancelarLaOrdenDeOtroUsuario() throws Exception {
        int idOrden = comprarOk(ana.bearer(), linea(general, 2)).get("idOrden").asInt();

        cancelar(luis.bearer(), idOrden).andExpect(status().isNotFound());
        cancelar(null, idOrden).andExpect(status().isUnauthorized());
        cancelar(ana.bearer(), 999999).andExpect(status().isNotFound());

        assertThat(entradas(idOrden, "VENDIDA")).isEqualTo(2);
        assertThat(contar("select count(*) from orden where id_orden = ? and estado = 'PAGADA'", idOrden)).isEqualTo(1);
    }

    @Test
    void variasCancelacionesSimultaneasSoloUnaProcede() throws Exception {
        int idOrden = comprarOk(ana.bearer(), linea(general, 3)).get("idOrden").asInt();

        List<Respuesta> respuestas = enParalelo(4, i -> () -> respuesta(cancelar(ana.bearer(), idOrden)));

        assertThat(respuestas.stream().filter(r -> r.estado() == 200)).hasSize(1);
        assertThat(respuestas.stream().filter(r -> r.estado() == 409)).hasSize(3);
        assertThat(entradas(idOrden, "CANCELADA")).isEqualTo(3);
    }
}
