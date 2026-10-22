package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CompraTest extends PruebaApiBase {

    private int evento;
    private int general;
    private int vip;

    @BeforeEach
    void preparar() throws Exception {
        evento = eventoPublicado().get("idEvento").asInt();
        general = crearTipoEntrada(evento, "General", "100.00", 10).get("idTipoEntrada").asInt();
        vip = crearTipoEntrada(evento, "VIP", "50.50", 5).get("idTipoEntrada").asInt();
    }

    private int vendidas(int idTipo) {
        return contar("select count(*) from entrada where id_tipo_entrada = ? and estado = 'VENDIDA'", idTipo);
    }

    @Test
    void laCompraCreaLaOrdenSusLineasYMarcaLasEntradasVendidas() throws Exception {
        Sesion ana = nuevaSesion();

        JsonNode orden = cuerpo(comprar(ana.bearer(), "clave-1", linea(general, 2), linea(vip, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.detalles.length()").value(2))
                .andExpect(jsonPath("$.detalles[0].linea").doesNotExist())
                .andExpect(jsonPath("$.detalles[0].idTipoEntrada").value(general))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(100.0))
                .andExpect(jsonPath("$.detalles[0].subtotal").value(200.0))
                .andExpect(jsonPath("$.detalles[1].subtotal").value(151.5)));

        int idOrden = orden.get("idOrden").asInt();
        assertThat(vendidas(general)).isEqualTo(2);
        assertThat(vendidas(vip)).isEqualTo(3);
        assertThat(contar("select count(*) from entrada where id_orden = ?", idOrden)).isEqualTo(5);
        assertThat(contar("select count(*) from entrada where id_tipo_entrada = ? and estado = 'DISPONIBLE'",
                general)).isEqualTo(8);
        // el total devuelto, el de la vista y la suma de subtotales coinciden
        BigDecimal suma = jdbc.queryForObject("select sum(subtotal) from detalle_compra where id_orden = ?",
                BigDecimal.class, idOrden);
        BigDecimal vista = jdbc.queryForObject("select total from v_orden_total where id_orden = ?",
                BigDecimal.class, idOrden);
        assertThat(suma).isEqualByComparingTo("351.50");
        assertThat(vista).isEqualByComparingTo(suma);
        assertThat(orden.get("total").decimalValue()).isEqualByComparingTo(suma);
    }

    @Test
    void elPrecioDeLaLineaEsElDelMomentoDeLaCompra() throws Exception {
        Sesion ana = nuevaSesion();
        int idOrden = comprarOk(ana.bearer(), linea(general, 1)).get("idOrden").asInt();

        mvc.perform(put("/api/v1/tipos-entrada/" + general).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "General", "precio", new BigDecimal("200.00"), "cupo", 10))))
                .andExpect(status().isOk());
        JsonNode nueva = comprarOk(ana.bearer(), linea(general, 1));

        mvc.perform(get("/api/v1/ordenes/" + idOrden).header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(100.0))
                .andExpect(jsonPath("$.total").value(100.0));
        assertThat(nueva.get("detalles").get(0).get("precioUnitario").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(jdbc.queryForObject("select precio_unitario from detalle_compra where id_orden = ?",
                BigDecimal.class, idOrden)).isEqualByComparingTo("100.00");
    }

    @Test
    void siFaltanEntradasSeResponde409() throws Exception {
        Sesion ana = nuevaSesion();

        comprar(ana.bearer(), "clave-1", linea(general, 11)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTRADAS_AGOTADAS"));
        assertThat(vendidas(general)).isZero();
    }

    @Test
    void unaCompraIncompletaNoDejaNingunaFilaParcial() throws Exception {
        Sesion ana = nuevaSesion();

        comprar(ana.bearer(), "clave-1", linea(general, 1), linea(vip, 99)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTRADAS_AGOTADAS"));

        assertThat(contar("select count(*) from orden")).isZero();
        assertThat(contar("select count(*) from detalle_compra")).isZero();
        assertThat(contar("select count(*) from entrada where estado <> 'DISPONIBLE' or id_orden is not null"))
                .isZero();
        // la clave no se consumió: se puede reutilizar en una compra válida
        comprar(ana.bearer(), "clave-1", linea(general, 1)).andExpect(status().isCreated());
    }

    @Test
    void repetirLaClaveDevuelveLaMismaOrdenSinConsumirMasEntradas() throws Exception {
        Sesion ana = nuevaSesion();

        JsonNode primera = cuerpo(comprar(ana.bearer(), "clave-1", linea(general, 2)).andExpect(status().isCreated()));
        JsonNode segunda = cuerpo(comprar(ana.bearer(), "clave-1", linea(general, 2)).andExpect(status().isOk()));

        assertThat(segunda.get("idOrden")).isEqualTo(primera.get("idOrden"));
        assertThat(segunda.get("total")).isEqualTo(primera.get("total"));
        assertThat(contar("select count(*) from orden")).isEqualTo(1);
        assertThat(vendidas(general)).isEqualTo(2);
    }

    @Test
    void laMismaClaveDeOtroUsuarioDa409() throws Exception {
        Sesion ana = nuevaSesion();
        Sesion luis = nuevaSesion();
        comprar(ana.bearer(), "clave-compartida", linea(general, 1)).andExpect(status().isCreated());

        comprar(luis.bearer(), "clave-compartida", linea(general, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        assertThat(vendidas(general)).isEqualTo(1);
    }

    @Test
    void variasSolicitudesSimultaneasConLaMismaClaveCreanUnaSolaOrden() throws Exception {
        Sesion ana = nuevaSesion();

        List<Respuesta> respuestas = enParalelo(8, i -> () ->
                respuesta(comprar(ana.bearer(), "clave-concurrente", linea(general, 2))));

        assertThat(respuestas).extracting(Respuesta::estado).containsOnly(200, 201);
        assertThat(respuestas.stream().filter(r -> r.estado() == 201)).hasSize(1);
        assertThat(respuestas.stream().map(r -> r.cuerpo().get("idOrden").asInt()).distinct()).hasSize(1);
        assertThat(contar("select count(*) from orden")).isEqualTo(1);
        assertThat(vendidas(general)).isEqualTo(2);
    }

    @Test
    void conMuchasComprasSimultaneasNuncaSeVendeMasQueElCupo() throws Exception {
        int cupo = 10;
        int tipo = crearTipoEntrada(evento, "Concurrencia", "10.00", cupo).get("idTipoEntrada").asInt();
        List<Sesion> compradores = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            compradores.add(nuevaSesion());
        }

        List<Respuesta> respuestas = enParalelo(compradores.size(), i -> () ->
                respuesta(comprar(compradores.get(i).bearer(), "clave-hilo-" + i, linea(tipo, 2))));

        long exitosas = respuestas.stream().filter(r -> r.estado() == 201).count();
        assertThat(respuestas.stream().filter(r -> r.estado() != 201))
                .allSatisfy(r -> {
                    assertThat(r.estado()).isEqualTo(409);
                    assertThat(r.codigo()).isEqualTo("ENTRADAS_AGOTADAS");
                });
        assertThat(exitosas).isBetween(1L, (long) cupo / 2);
        assertThat(vendidas(tipo)).isEqualTo((int) exitosas * 2);
        assertThat(contar("select count(*) from entrada where id_tipo_entrada = ?", tipo)).isEqualTo(cupo);
        // cada orden tiene exactamente las entradas que compró, y ninguna entrada está en dos órdenes
        assertThat(contar("select count(*) from detalle_compra d where d.cantidad <> "
                + "(select count(*) from entrada e where e.id_orden = d.id_orden and e.id_tipo_entrada = d.id_tipo_entrada)"))
                .isZero();
        assertThat(contar("select count(*) from orden")).isEqualTo((int) exitosas);

        // las que quedaron se pueden comprar después, y pasadas esas ya no hay más
        int restantes = cupo - vendidas(tipo);
        if (restantes > 0) {
            comprar(compradores.get(0).bearer(), "clave-final", linea(tipo, restantes)).andExpect(status().isCreated());
        }
        comprar(compradores.get(0).bearer(), "clave-sobrante", linea(tipo, 1)).andExpect(status().isConflict());
        assertThat(vendidas(tipo)).isEqualTo(cupo);
    }

    @Test
    void rechazaSolicitudesInvalidas() throws Exception {
        Sesion ana = nuevaSesion();

        comprar(ana.bearer(), "clave-1").andExpect(status().isUnprocessableEntity());
        comprar(ana.bearer(), "clave-1", linea(general, 0)).andExpect(status().isUnprocessableEntity());
        comprar(ana.bearer(), "clave-1", linea(general, -1)).andExpect(status().isUnprocessableEntity());
        comprar(ana.bearer(), "clave-1", linea(general, 1), linea(general, 2)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        comprar(ana.bearer(), " ", linea(general, 1)).andExpect(status().isUnprocessableEntity());
        comprar(ana.bearer(), "x".repeat(65), linea(general, 1)).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/ordenes").header("Authorization", ana.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnprocessableEntity());
        comprar(ana.bearer(), "clave-1", linea(999999, 1)).andExpect(status().isNotFound());
        assertThat(contar("select count(*) from orden")).isZero();
    }

    @Test
    void noSeVendenEntradasDeUnEventoNoPublicado() throws Exception {
        int escenario = crearEscenario(unico("Ciudad")).get("idEscenario").asInt();
        int borrador = crearEvento(escenario, 0, 3).get("idEvento").asInt();
        int tipo = crearTipoEntrada(borrador, "General", "10.00", 3).get("idTipoEntrada").asInt();
        Sesion ana = nuevaSesion();

        comprar(ana.bearer(), "clave-1", linea(tipo, 1)).andExpect(status().isNotFound());

        publicar(borrador);
        comprar(ana.bearer(), "clave-1", linea(tipo, 1)).andExpect(status().isCreated());
        cambiarEstado(borrador, "CANCELADO").andExpect(status().isOk());
        comprar(ana.bearer(), "clave-2", linea(tipo, 1)).andExpect(status().isNotFound());
    }

    @Test
    void comprarExigeAutenticacion() throws Exception {
        mvc.perform(post("/api/v1/ordenes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("claveIdempotencia", "c", "lineas", List.of(linea(general, 1))))))
                .andExpect(status().isUnauthorized());
        comprar("Bearer invalido", "c", linea(general, 1)).andExpect(status().isUnauthorized());
    }
}
