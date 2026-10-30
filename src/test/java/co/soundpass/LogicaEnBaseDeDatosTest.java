package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.error.SqlEstado;
import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Triggers, procedimientos y funciones de db/init/02_logica_en_base_de_datos.sql. */
class LogicaEnBaseDeDatosTest extends PruebaApiBase {

    private int evento;
    private int general;
    private int vip;

    @BeforeEach
    void preparar() throws Exception {
        evento = eventoPublicado().get("idEvento").asInt();
        general = crearTipoEntrada(evento, "General", "100.00", 6).get("idTipoEntrada").asInt();
        vip = crearTipoEntrada(evento, "VIP", "50.00", 3).get("idTipoEntrada").asInt();
    }

    private void falla(String sqlState, Runnable operacion) {
        assertThatThrownBy(operacion::run).satisfies(e -> assertThat(SqlEstado.de(e)).isEqualTo(sqlState));
    }

    private int entrada(String estado) {
        return jdbc.queryForObject("select min(id_entrada) from entrada where estado = ?", Integer.class, estado);
    }

    private void estado(int idEntrada, String nuevo) {
        jdbc.update("update entrada set estado = ? where id_entrada = ?", nuevo, idEntrada);
    }

    private int idAdmin() {
        return jdbc.queryForObject("select min(id_usuario) from usuario where rol = 'ADMIN'", Integer.class);
    }

    // ----------------------------------------------------------------- triggers

    @Test
    void laEntradaSoloAdmiteLasTransicionesDeLaRegla5() throws Exception {
        comprarOk(nuevaSesion().bearer(), linea(general, 3));
        int disponible = entrada("DISPONIBLE");

        falla("SP409", () -> estado(disponible, "USADA"));
        falla("SP409", () -> estado(disponible, "CANCELADA"));

        int vendida = entrada("VENDIDA");
        falla("SP409", () -> estado(vendida, "DISPONIBLE"));
        estado(vendida, "USADA");
        falla("SP409", () -> estado(vendida, "VENDIDA"));
        falla("SP409", () -> estado(vendida, "CANCELADA"));

        int otra = entrada("VENDIDA");
        estado(otra, "CANCELADA");
        falla("SP409", () -> estado(otra, "VENDIDA"));
        falla("SP409", () -> estado(otra, "USADA"));
    }

    @Test
    void unaLineaDeCompraNoCambiaDespuesDeCreada() throws Exception {
        comprarOk(nuevaSesion().bearer(), linea(general, 2));

        falla("SP409", () -> jdbc.update("update detalle_compra set precio_unitario = 1"));
        falla("SP409", () -> jdbc.update("update detalle_compra set cantidad = 1"));
        assertThat(jdbc.queryForObject("select precio_unitario from detalle_compra", java.math.BigDecimal.class))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void noSeBorraFisicamenteNiUsuariosNiCatalogoNiOrdenes() throws Exception {
        comprarOk(nuevaSesion().bearer(), linea(general, 2));
        crearArtista("rock");

        for (String tabla : List.of("usuario", "artista", "escenario", "evento", "orden")) {
            falla("SP409", () -> jdbc.update("delete from " + tabla));
            assertThat(contar("select count(*) from " + tabla)).as(tabla).isPositive();
        }
    }

    @Test
    void soloSePuedeBorrarUnaEntradaDisponible() throws Exception {
        comprarOk(nuevaSesion().bearer(), linea(general, 1));
        int vendida = entrada("VENDIDA");
        int disponible = entrada("DISPONIBLE");

        falla("SP409", () -> jdbc.update("delete from entrada where id_entrada = ?", vendida));
        assertThat(jdbc.update("delete from entrada where id_entrada = ?", disponible)).isEqualTo(1);
    }

    @Test
    void elAsistenteSoloSeRegistraEnUnaEntradaVendida() throws Exception {
        comprarOk(nuevaSesion().bearer(), linea(general, 1));
        int disponible = entrada("DISPONIBLE");
        int vendida = entrada("VENDIDA");

        falla("SP409", () -> jdbc.update(
                "insert into asistente (id_entrada, nombre, documento) values (?, 'Ana', '1')", disponible));
        assertThat(jdbc.update(
                "insert into asistente (id_entrada, nombre, documento) values (?, 'Ana', '1')", vendida)).isEqualTo(1);
    }

    @Test
    void laAuditoriaRegistraCompraCancelacionYCheckinConSuUsuario() throws Exception {
        Sesion ana = nuevaSesion();
        int idOrden = comprarOk(ana.bearer(), linea(general, 2)).get("idOrden").asInt();
        int idAna = ana.usuario().getId();

        assertThat(contar("select count(*) from auditoria_estado where tabla = 'orden' and id_registro = ? "
                + "and estado_anterior is null and estado_nuevo = 'PAGADA' and id_usuario = ?", idOrden, idAna))
                .isEqualTo(1);
        assertThat(contar("select count(*) from auditoria_estado where tabla = 'entrada' "
                + "and estado_anterior = 'DISPONIBLE' and estado_nuevo = 'VENDIDA' and id_usuario = ?", idAna))
                .isEqualTo(2);

        mvc.perform(post("/api/v1/ordenes/" + idOrden + "/cancelacion").header("Authorization", ana.bearer()))
                .andExpect(status().isOk());

        assertThat(contar("select count(*) from auditoria_estado where tabla = 'orden' and id_registro = ? "
                + "and estado_anterior = 'PAGADA' and estado_nuevo = 'REEMBOLSADA' and id_usuario = ?", idOrden, idAna))
                .isEqualTo(1);
        assertThat(contar("select count(*) from auditoria_estado where tabla = 'entrada' "
                + "and estado_anterior = 'VENDIDA' and estado_nuevo = 'CANCELADA' and id_usuario = ?", idAna))
                .isEqualTo(2);

        // check-in de otra entrada: lo registra el ADMIN
        JsonNode otra = comprarOk(ana.bearer(), linea(vip, 1));
        JsonNode entrada = cuerpo(entradasMias(ana.bearer(), "estado", "VENDIDA")).get("contenido").get(0);
        mvc.perform(put("/api/v1/entradas/" + entrada.get("idEntrada").asInt() + "/asistente")
                        .header("Authorization", ana.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Luisa", "documento", "9"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/checkins").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codigo", entrada.get("codigo").asText()))))
                .andExpect(status().isOk());

        assertThat(contar("select count(*) from auditoria_estado where tabla = 'entrada' and id_registro = ? "
                + "and estado_anterior = 'VENDIDA' and estado_nuevo = 'USADA' and id_usuario = ?",
                entrada.get("idEntrada").asInt(), idAdmin())).isEqualTo(1);
        assertThat(otra.get("idOrden").asInt()).isPositive();
    }

    // ------------------------------------------------------ procedimientos

    @Test
    void generarEntradasValidaSusArgumentos() {
        falla("SP404", () -> jdbc.queryForObject("call sp_generar_entradas(?, ?, null)", Integer.class, 999999, 5));
        falla("SP422", () -> jdbc.queryForObject("call sp_generar_entradas(?, ?, null)", Integer.class, general, 0));
        falla("SP422", () -> jdbc.queryForObject("call sp_generar_entradas(?, ?, null)", Integer.class, general,
                100001));
        assertThat(jdbc.queryForObject("call sp_generar_entradas(?, ?, null)", Integer.class, general, 4))
                .isEqualTo(4);
        assertThat(contar("select count(*) from entrada where id_tipo_entrada = ?", general)).isEqualTo(10);
    }

    @Test
    void comprarDirectamenteConLaFuncionAplicaLasReglas() throws Exception {
        int usuario = nuevaSesion().usuario().getId();
        String unaLinea = "[{\"idTipoEntrada\":" + general + ",\"cantidad\":2}]";

        var compra = jdbc.queryForMap("select * from fn_comprar_entradas(?, 'c1', ?::jsonb)", usuario, unaLinea);
        var repetida = jdbc.queryForMap("select * from fn_comprar_entradas(?, 'c1', ?::jsonb)", usuario, unaLinea);

        assertThat(compra.get("creada")).isEqualTo(true);
        assertThat(repetida.get("creada")).isEqualTo(false);
        assertThat(repetida.get("orden_id")).isEqualTo(compra.get("orden_id"));
        falla("SP422", () -> jdbc.queryForMap("select * from fn_comprar_entradas(?, 'c2', '[]'::jsonb)", usuario));
        falla("SP422", () -> jdbc.queryForMap("select * from fn_comprar_entradas(?, 'c3', ?::jsonb)", usuario,
                "[{\"idTipoEntrada\":" + general + ",\"cantidad\":1},{\"idTipoEntrada\":" + general + ",\"cantidad\":1}]"));
        falla("SP410", () -> jdbc.queryForMap("select * from fn_comprar_entradas(?, 'c4', ?::jsonb)", usuario,
                "[{\"idTipoEntrada\":" + vip + ",\"cantidad\":50}]"));
        assertThat(contar("select count(*) from orden")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- reporte

    @Test
    void elReporteDeVentasCuentaPorTipoYSoloSumaOrdenesPagadas() throws Exception {
        Sesion ana = nuevaSesion();
        Sesion luis = nuevaSesion();
        comprarOk(ana.bearer(), linea(general, 2), linea(vip, 1));
        int ordenLuis = comprarOk(luis.bearer(), linea(general, 1)).get("idOrden").asInt();
        mvc.perform(post("/api/v1/ordenes/" + ordenLuis + "/cancelacion").header("Authorization", luis.bearer()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/eventos/" + evento + "/ventas").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("General"))
                .andExpect(jsonPath("$[0].cupo").value(6))
                .andExpect(jsonPath("$[0].disponibles").value(3))
                .andExpect(jsonPath("$[0].vendidas").value(2))
                .andExpect(jsonPath("$[0].canceladas").value(1))
                .andExpect(jsonPath("$[0].usadas").value(0))
                .andExpect(jsonPath("$[0].ingresos").value(200.0))
                .andExpect(jsonPath("$[1].nombre").value("VIP"))
                .andExpect(jsonPath("$[1].vendidas").value(1))
                .andExpect(jsonPath("$[1].ingresos").value(50.0));
    }

    @Test
    void elReporteDeVentasEsSoloParaAdminYElEventoDebeExistir() throws Exception {
        String ruta = "/api/v1/eventos/" + evento + "/ventas";

        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta).header("Authorization", usuario)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos/999999/ventas").header("Authorization", admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
