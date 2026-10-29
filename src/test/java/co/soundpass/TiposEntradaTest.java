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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TiposEntradaTest extends PruebaApiBase {

    private int contar(int idTipo, String estado) {
        return jdbc.queryForObject("select count(*) from entrada where id_tipo_entrada = ? and estado = ?",
                Integer.class, idTipo, estado);
    }

    private int total(int idTipo) {
        return jdbc.queryForObject("select count(*) from entrada where id_tipo_entrada = ?", Integer.class, idTipo);
    }

    /** Marca n entradas del tipo como vendidas (con una orden creada a mano) para simular compras. */
    private void vender(int idTipo, int n, String estado) {
        int idUsuario = jdbc.queryForObject("select id_usuario from usuario limit 1", Integer.class);
        int idOrden = jdbc.queryForObject("insert into orden (id_usuario, clave_idempotencia) values (?, ?) "
                + "returning id_orden", Integer.class, idUsuario, unico("clave"));
        for (Integer id : jdbc.queryForList("select id_entrada from entrada where id_tipo_entrada = ? "
                + "and estado = 'DISPONIBLE' order by id_entrada limit ?", Integer.class, idTipo, n)) {
            jdbc.update("update entrada set estado = 'VENDIDA', id_orden = ? where id_entrada = ?", idOrden, id);
            if (estado.equals("USADA")) {
                jdbc.update("update entrada set estado = 'USADA' where id_entrada = ?", id);
            }
        }
    }

    private org.springframework.test.web.servlet.ResultActions actualizar(int idTipo, String token, Object cuerpo)
            throws Exception {
        var peticion = put("/api/v1/tipos-entrada/" + idTipo).contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo));
        return mvc.perform(token == null ? peticion : peticion.header("Authorization", token));
    }

    private static Map<String, Object> datos(String nombre, String precio, int cupo) {
        return Map.of("nombre", nombre, "precio", new BigDecimal(precio), "cupo", cupo);
    }

    @Test
    void crearUnTipoGeneraTantasEntradasComoElCupo() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();

        JsonNode tipo = crearTipoEntrada(idEvento, "VIP", "150000.00", 5);
        int idTipo = tipo.get("idTipoEntrada").asInt();

        assertThat(tipo.get("cupo").asInt()).isEqualTo(5);
        assertThat(total(idTipo)).isEqualTo(5);
        assertThat(contar(idTipo, "DISPONIBLE")).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(distinct codigo) from entrada where id_tipo_entrada = ?",
                Integer.class, idTipo)).isEqualTo(5);
        mvc.perform(get("/api/v1/eventos/" + idEvento + "/tipos-entrada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("VIP"))
                .andExpect(jsonPath("$[0].precio").value(150000.0));
    }

    @Test
    void rechazaPrecioYCupoInvalidos() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        String ruta = "/api/v1/eventos/" + idEvento + "/tipos-entrada";
        Map<String, Object> sinNombre = new HashMap<>(datos("x", "10.00", 5));
        sinNombre.put("nombre", " ");

        for (Object invalido : new Object[] {
                datos("A", "-1.00", 5), datos("A", "10.00", 0), datos("A", "10.00", -3),
                datos("A", "10.00", 100001), datos("A", "10.005", 5), sinNombre}) {
            mvc.perform(post(ruta).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalido)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
        assertThat(jdbc.queryForObject("select count(*) from tipo_entrada", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from entrada", Integer.class)).isZero();
    }

    @Test
    void unPrecioEnCeroEsValido() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        crearTipoEntrada(idEvento, "Cortesía", "0.00", 2);
    }

    @Test
    void elNombreDelTipoNoSeRepiteEnElMismoEvento() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        crearTipoEntrada(idEvento, "General", "100.00", 2);

        mvc.perform(post("/api/v1/eventos/" + idEvento + "/tipos-entrada").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datos("General", "50.00", 3))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        assertThat(jdbc.queryForObject("select count(*) from entrada", Integer.class)).isEqualTo(2);
        crearTipoEntrada(eventoPublicado().get("idEvento").asInt(), "General", "100.00", 1);
    }

    @Test
    void laDisponibilidadBajaCuandoUnaEntradaCambiaDeEstado() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        int idTipo = crearTipoEntrada(idEvento, "General", "100.00", 5).get("idTipoEntrada").asInt();
        crearTipoEntrada(idEvento, "VIP", "300.00", 2);

        mvc.perform(get("/api/v1/eventos/" + idEvento + "/disponibilidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("General"))
                .andExpect(jsonPath("$[0].cupo").value(5))
                .andExpect(jsonPath("$[0].disponibles").value(5))
                .andExpect(jsonPath("$[1].disponibles").value(2));

        vender(idTipo, 1, "VENDIDA");

        mvc.perform(get("/api/v1/eventos/" + idEvento + "/disponibilidad"))
                .andExpect(jsonPath("$[0].disponibles").value(4))
                .andExpect(jsonPath("$[1].disponibles").value(2));
    }

    @Test
    void unTipoAgotadoApareceConCeroDisponibles() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        int idTipo = crearTipoEntrada(idEvento, "General", "100.00", 2).get("idTipoEntrada").asInt();
        vender(idTipo, 2, "VENDIDA");

        mvc.perform(get("/api/v1/eventos/" + idEvento + "/disponibilidad"))
                .andExpect(jsonPath("$[0].disponibles").value(0));
    }

    @Test
    void noSePuedeReducirElCupoPorDebajoDeLasEntradasVendidas() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        int idTipo = crearTipoEntrada(idEvento, "General", "100.00", 5).get("idTipoEntrada").asInt();
        vender(idTipo, 2, "VENDIDA");
        vender(idTipo, 1, "USADA");

        actualizar(idTipo, admin, datos("General", "100.00", 2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CUPO_INSUFICIENTE"));
        assertThat(total(idTipo)).isEqualTo(5);

        actualizar(idTipo, admin, datos("General", "120.00", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cupo").value(3))
                .andExpect(jsonPath("$.precio").value(120.0));
        assertThat(total(idTipo)).isEqualTo(3);
        assertThat(contar(idTipo, "DISPONIBLE")).isZero();
        assertThat(contar(idTipo, "VENDIDA")).isEqualTo(2);
        assertThat(contar(idTipo, "USADA")).isEqualTo(1);
    }

    @Test
    void reducirElCupoQuitaSoloEntradasDisponibles() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        int idTipo = crearTipoEntrada(idEvento, "General", "100.00", 6).get("idTipoEntrada").asInt();
        vender(idTipo, 2, "VENDIDA");

        actualizar(idTipo, admin, datos("General", "100.00", 4)).andExpect(status().isOk());

        assertThat(total(idTipo)).isEqualTo(4);
        assertThat(contar(idTipo, "VENDIDA")).isEqualTo(2);
        assertThat(contar(idTipo, "DISPONIBLE")).isEqualTo(2);
    }

    @Test
    void aumentarElCupoGeneraEntradasNuevas() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        int idTipo = crearTipoEntrada(idEvento, "General", "100.00", 5).get("idTipoEntrada").asInt();

        actualizar(idTipo, admin, datos("General", "100.00", 8)).andExpect(status().isOk());

        assertThat(total(idTipo)).isEqualTo(8);
        assertThat(contar(idTipo, "DISPONIBLE")).isEqualTo(8);
        actualizar(999999, admin, datos("General", "100.00", 8)).andExpect(status().isNotFound());
    }

    @Test
    void soloAdminCreaYEditaTipos() throws Exception {
        int idEvento = eventoPublicado().get("idEvento").asInt();
        int idTipo = crearTipoEntrada(idEvento, "General", "100.00", 2).get("idTipoEntrada").asInt();
        String ruta = "/api/v1/eventos/" + idEvento + "/tipos-entrada";
        String cuerpo = json(datos("Nuevo", "10.00", 3));

        mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ruta).header("Authorization", usuario).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andExpect(status().isForbidden());
        actualizar(idTipo, null, datos("General", "1.00", 2)).andExpect(status().isUnauthorized());
        actualizar(idTipo, usuario, datos("General", "1.00", 2)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/eventos/999999/tipos-entrada").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
    }

    @Test
    void losTiposDeUnEventoNoPublicadoSoloLosVeUnAdmin() throws Exception {
        int idEscenario = crearEscenario(unico("Ciudad")).get("idEscenario").asInt();
        int idEvento = crearEvento(idEscenario, 0, 3).get("idEvento").asInt();
        crearTipoEntrada(idEvento, "General", "100.00", 2);
        String tipos = "/api/v1/eventos/" + idEvento + "/tipos-entrada";
        String disponibilidad = "/api/v1/eventos/" + idEvento + "/disponibilidad";

        mvc.perform(get(tipos)).andExpect(status().isNotFound());
        mvc.perform(get(disponibilidad)).andExpect(status().isNotFound());
        mvc.perform(get(tipos).header("Authorization", admin)).andExpect(status().isOk());

        publicar(idEvento);
        mvc.perform(get(tipos)).andExpect(status().isOk());
        mvc.perform(get(disponibilidad)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/999999/disponibilidad")).andExpect(status().isNotFound());
    }
}
