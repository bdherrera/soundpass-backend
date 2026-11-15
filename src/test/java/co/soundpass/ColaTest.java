package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.config.MongoEsquema;
import co.soundpass.soporte.PruebaApiBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Cola de reserva con contadores precalculados (patrón Computed). */
class ColaTest extends PruebaApiBase {

    private int evento;
    private int tipo;

    @BeforeEach
    void preparar() throws Exception {
        evento = eventoPublicado().get("idEvento").asInt();
        tipo = crearTipoEntrada(evento, "General", "100.00", 40).get("idTipoEntrada").asInt();
    }

    private String ruta(String resto) {
        return "/api/v1/eventos/" + evento + "/cola" + resto;
    }

    private ResultActions configurar(String bearer, Object habilitada, Object ventana, Object ttl) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("habilitada", habilitada);
        cuerpo.put("ventana", ventana);
        cuerpo.put("ttlMinutos", ttl);
        var peticion = put(ruta("")).contentType(MediaType.APPLICATION_JSON).content(json(cuerpo));
        return mvc.perform(bearer == null ? peticion : peticion.header("Authorization", bearer));
    }

    private void habilitar(int ventana, int ttl) throws Exception {
        configurar(admin, true, ventana, ttl).andExpect(status().isOk());
    }

    private ResultActions unirse(Sesion s) throws Exception {
        return mvc.perform(post(ruta("/turnos")).header("Authorization", s.bearer()));
    }

    private JsonNode miTurno(Sesion s) throws Exception {
        return cuerpo(mvc.perform(get(ruta("/turnos/mio")).header("Authorization", s.bearer()))
                .andExpect(status().isOk()));
    }

    private Document cola() {
        return mongo.getCollection(MongoEsquema.COLA_EVENTO).find(Filters.eq("id_evento", evento)).first();
    }

    private long contadores(String campo) {
        return ((Number) cola().get(campo)).longValue();
    }

    private long turnosEn(String estado) {
        return mongo.getCollection(MongoEsquema.TURNOS_COLA)
                .countDocuments(Filters.and(Filters.eq("id_evento", evento), Filters.eq("estado", estado)));
    }

    private ResultActions comprarTipo(Sesion s, String clave) throws Exception {
        return comprar(s.bearer(), clave, linea(tipo, 1));
    }

    // ----------------------------------------------------------------- admisión

    @Test
    void lasPersonasDentroDeLaVentanaPasanYLasDemasEsperan() throws Exception {
        habilitar(2, 15);
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        Sesion c = nuevaSesion();

        unirse(a).andExpect(status().isOk()).andExpect(jsonPath("$.turno").value(1))
                .andExpect(jsonPath("$.estado").value("ADMITIDO")).andExpect(jsonPath("$.posicion").value(0))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty());
        unirse(b).andExpect(jsonPath("$.turno").value(2)).andExpect(jsonPath("$.estado").value("ADMITIDO"));
        unirse(c).andExpect(jsonPath("$.turno").value(3)).andExpect(jsonPath("$.estado").value("ESPERANDO"))
                .andExpect(jsonPath("$.posicion").value(1)).andExpect(jsonPath("$.expiraEn").doesNotExist());

        mvc.perform(get(ruta("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(true))
                .andExpect(jsonPath("$.ventana").value(2))
                .andExpect(jsonPath("$.enEspera").value(1))
                .andExpect(jsonPath("$.enCompra").value(2));
        // los contadores precalculados son los que responden
        assertThat(contadores("siguiente_turno")).isEqualTo(4);
        assertThat(contadores("ultimo_admitido")).isEqualTo(2);
        assertThat(contadores("activos")).isEqualTo(2);
    }

    @Test
    void entrarALaColaEsIdempotente() throws Exception {
        habilitar(1, 15);
        Sesion a = nuevaSesion();

        unirse(a).andExpect(jsonPath("$.turno").value(1));
        unirse(a).andExpect(jsonPath("$.turno").value(1));

        assertThat(contadores("siguiente_turno")).isEqualTo(2);
        assertThat(turnosEn("ADMITIDO")).isEqualTo(1);
    }

    @Test
    void sinTurnoAdmitidoNoSePuedeComprarYAlTerminarPasaElSiguiente() throws Exception {
        habilitar(1, 15);
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        Sesion sinFila = nuevaSesion();
        unirse(a);
        unirse(b).andExpect(jsonPath("$.estado").value("ESPERANDO"));

        comprarTipo(b, "b-1").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("COLA_REQUERIDA"));
        comprarTipo(sinFila, "s-1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COLA_REQUERIDA"));
        assertThat(contar("select count(*) from orden")).isZero();

        comprarTipo(a, "a-1").andExpect(status().isCreated());
        assertThat(miTurno(a).get("estado").asText()).isEqualTo("COMPLETADO");
        JsonNode turnoB = miTurno(b);
        assertThat(turnoB.get("estado").asText()).isEqualTo("ADMITIDO");
        assertThat(turnoB.get("posicion").asInt()).isZero();
        assertThat(contadores("activos")).isEqualTo(1);

        comprarTipo(b, "b-2").andExpect(status().isCreated());
        assertThat(contadores("activos")).isZero();
        assertThat(contar("select count(*) from orden")).isEqualTo(2);
    }

    @Test
    void repetirLaCompraConLaMismaClaveNoVuelveAPasarPorLaCola() throws Exception {
        habilitar(1, 15);
        Sesion a = nuevaSesion();
        unirse(a);
        comprarTipo(a, "clave-a").andExpect(status().isCreated());

        comprarTipo(a, "clave-a").andExpect(status().isOk());
        comprarTipo(a, "clave-otra").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COLA_REQUERIDA"));
        assertThat(contar("select count(*) from orden")).isEqualTo(1);
    }

    @Test
    void salirDeLaColaLiberaElLugar() throws Exception {
        habilitar(1, 15);
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        unirse(a);
        unirse(b);

        mvc.perform(delete(ruta("/turnos/mio")).header("Authorization", a.bearer())).andExpect(status().isNoContent());

        assertThat(miTurno(a).get("estado").asText()).isEqualTo("CANCELADO");
        assertThat(miTurno(b).get("estado").asText()).isEqualTo("ADMITIDO");
        mvc.perform(delete(ruta("/turnos/mio")).header("Authorization", a.bearer())).andExpect(status().isNotFound());
        unirse(a).andExpect(jsonPath("$.estado").value("ESPERANDO")).andExpect(jsonPath("$.turno").value(3));
    }

    @Test
    void unTurnoQueSalioEsperandoNoDesperdiciaElLugarDeLosDemas() throws Exception {
        habilitar(1, 15);
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        Sesion c = nuevaSesion();
        unirse(a);
        unirse(b);
        unirse(c).andExpect(jsonPath("$.posicion").value(2));

        mvc.perform(delete(ruta("/turnos/mio")).header("Authorization", b.bearer())).andExpect(status().isNoContent());
        comprarTipo(a, "a-1").andExpect(status().isCreated());

        // el turno 2 se canceló: el lugar pasa al 3 en vez de quedar vacío
        assertThat(miTurno(c).get("estado").asText()).isEqualTo("ADMITIDO");
        assertThat(contadores("activos")).isEqualTo(1);
    }

    @Test
    void losTurnosAdmitidosQueNoCompranVencen() throws Exception {
        habilitar(1, 1);
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        unirse(a);
        unirse(b);
        mongo.getCollection(MongoEsquema.TURNOS_COLA).updateOne(
                Filters.and(Filters.eq("id_evento", evento), Filters.eq("id_usuario", a.usuario().getId())),
                Updates.set("expira_en", Date.from(Instant.now().minusSeconds(60))));

        assertThat(miTurno(b).get("estado").asText()).isEqualTo("ADMITIDO");
        assertThat(miTurno(a).get("estado").asText()).isEqualTo("EXPIRADO");
        comprarTipo(a, "a-1").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("COLA_REQUERIDA"));
        assertThat(contadores("activos")).isEqualTo(1);
        unirse(a).andExpect(jsonPath("$.estado").value("ESPERANDO"));
    }

    @Test
    void unaCompraDeVariosEventosExigeTurnoEnCadaUnoQueTengaCola() throws Exception {
        int otroEvento = eventoPublicado().get("idEvento").asInt();
        int otroTipo = crearTipoEntrada(otroEvento, "VIP", "50.00", 5).get("idTipoEntrada").asInt();
        habilitar(1, 15);
        Sesion a = nuevaSesion();
        unirse(a);

        comprar(a.bearer(), "multi-1", linea(tipo, 1), linea(otroTipo, 1)).andExpect(status().isCreated());
        assertThat(miTurno(a).get("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(contar("select count(*) from detalle_compra")).isEqualTo(2);
    }

    // ----------------------------------------------------------- configuración

    @Test
    void sinColaHabilitadaNoSePuedeEntrarYLaCompraNoSeAfecta() throws Exception {
        Sesion a = nuevaSesion();

        mvc.perform(get(ruta(""))).andExpect(jsonPath("$.habilitada").value(false))
                .andExpect(jsonPath("$.enEspera").value(0));
        unirse(a).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("COLA_INACTIVA"));
        comprarTipo(a, "libre-1").andExpect(status().isCreated());

        configurar(admin, false, 5, 15).andExpect(status().isOk()).andExpect(jsonPath("$.habilitada").value(false));
        unirse(a).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("COLA_INACTIVA"));
        comprarTipo(a, "libre-2").andExpect(status().isCreated());
    }

    @Test
    void deshabilitarLaColaDejaComprarSinTurno() throws Exception {
        habilitar(1, 15);
        Sesion sinFila = nuevaSesion();
        comprarTipo(sinFila, "x-1").andExpect(status().isConflict());

        configurar(admin, false, 1, 15).andExpect(status().isOk());

        comprarTipo(sinFila, "x-2").andExpect(status().isCreated());
    }

    @Test
    void soloAdminConfiguraLaColaYSeValidanLosDatos() throws Exception {
        configurar(null, true, 5, 15).andExpect(status().isUnauthorized());
        configurar(usuario, true, 5, 15).andExpect(status().isForbidden());
        configurar(admin, true, 0, 15).andExpect(status().isUnprocessableEntity());
        configurar(admin, true, 1001, 15).andExpect(status().isUnprocessableEntity());
        configurar(admin, true, 5, 0).andExpect(status().isUnprocessableEntity());
        configurar(admin, true, 5, 121).andExpect(status().isUnprocessableEntity());
        configurar(admin, null, 5, 15).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/eventos/999999/cola").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("habilitada", true, "ventana", 5, "ttlMinutos", 15))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reconfigurarConservaLosContadores() throws Exception {
        habilitar(1, 15);
        unirse(nuevaSesion());
        unirse(nuevaSesion());

        configurar(admin, true, 3, 30).andExpect(status().isOk()).andExpect(jsonPath("$.ventana").value(3))
                .andExpect(jsonPath("$.ttlMinutos").value(30));

        assertThat(contadores("siguiente_turno")).isEqualTo(3);
        // la ventana más grande deja pasar al que esperaba
        mvc.perform(get(ruta(""))).andExpect(jsonPath("$.enCompra").value(2)).andExpect(jsonPath("$.enEspera").value(0));
    }

    @Test
    void lasOperacionesDeUsuarioExigenAutenticacionYTurno() throws Exception {
        habilitar(1, 15);

        mvc.perform(post(ruta("/turnos"))).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta("/turnos/mio"))).andExpect(status().isUnauthorized());
        mvc.perform(delete(ruta("/turnos/mio"))).andExpect(status().isUnauthorized());
        mvc.perform(get(ruta("/turnos/mio")).header("Authorization", usuario)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- concurrencia

    @Test
    void muchosUsuariosSimultaneosRecibenTurnosUnicosYContadoresConsistentes() throws Exception {
        habilitar(5, 15);
        List<Sesion> sesiones = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            sesiones.add(nuevaSesion());
        }

        List<Respuesta> respuestas = enParalelo(sesiones.size(), i -> () -> respuesta(unirse(sesiones.get(i))));

        assertThat(respuestas).extracting(Respuesta::estado).containsOnly(200);
        assertThat(respuestas.stream().map(r -> r.cuerpo().get("turno").asInt()).sorted().toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 30).boxed().toList());
        // nunca más admitidos que la ventana, y cada contador coincide con los documentos reales
        assertThat(contadores("siguiente_turno")).isEqualTo(31);
        assertThat(contadores("activos")).isEqualTo(5);
        assertThat(contadores("en_espera")).isEqualTo(25);
        assertThat(turnosEn("ADMITIDO")).isEqualTo(5);
        assertThat(turnosEn("ESPERANDO")).isEqualTo(25);
        Document masAlto = mongo.getCollection(MongoEsquema.TURNOS_COLA)
                .find(Filters.and(Filters.eq("id_evento", evento), Filters.eq("estado", "ADMITIDO")))
                .sort(com.mongodb.client.model.Sorts.descending("turno")).first();
        assertThat(contadores("ultimo_admitido")).isEqualTo(((Number) masAlto.get("turno")).longValue());
        mvc.perform(get(ruta(""))).andExpect(jsonPath("$.enEspera").value(25)).andExpect(jsonPath("$.enCompra").value(5));
    }

    @Test
    void elMismoUsuarioEntrandoVariasVecesAlaVezTieneUnSoloTurno() throws Exception {
        habilitar(1, 15);
        Sesion a = nuevaSesion();

        List<Respuesta> respuestas = enParalelo(8, i -> () -> respuesta(unirse(a)));

        assertThat(respuestas).extracting(Respuesta::estado).containsOnly(200);
        assertThat(respuestas.stream().map(r -> r.cuerpo().get("turno").asInt()).distinct()).hasSize(1);
        assertThat(mongo.getCollection(MongoEsquema.TURNOS_COLA)
                .countDocuments(Filters.and(Filters.eq("id_evento", evento), Filters.eq("activo", true)))).isEqualTo(1);
    }

    @Test
    void conMuchasComprasSimultaneasCadaTurnoCompraUnaVezYLaVentanaNuncaSeExcede() throws Exception {
        habilitar(4, 15);
        List<Sesion> sesiones = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Sesion s = nuevaSesion();
            sesiones.add(s);
            unirse(s);
        }

        // todos intentan comprar a la vez: los admitidos compran y, al terminar, dejan pasar a otros que todavía
        // pueden llegar a tiempo; el resto recibe 409 COLA_REQUERIDA
        List<Respuesta> respuestas = enParalelo(sesiones.size(), i -> () ->
                respuesta(comprarTipo(sesiones.get(i), "ronda1-" + i)));

        long compras = respuestas.stream().filter(r -> r.estado() == 201).count();
        assertThat(compras).isBetween(4L, 12L);
        assertThat(respuestas.stream().filter(r -> r.estado() != 201))
                .allSatisfy(r -> {
                    assertThat(r.estado()).isEqualTo(409);
                    assertThat(r.codigo()).isEqualTo("COLA_REQUERIDA");
                });
        assertThat(turnosEn("COMPLETADO")).isEqualTo(compras);
        assertThat(contar("select count(*) from orden")).isEqualTo((int) compras);
        // los contadores siguen coincidiendo con los turnos reales y nunca hay más admitidos que la ventana
        assertThat(turnosEn("ADMITIDO")).isLessThanOrEqualTo(4);
        assertThat(contadores("activos")).isEqualTo(turnosEn("ADMITIDO"));
        assertThat(contadores("en_espera")).isEqualTo(turnosEn("ESPERANDO"));
        assertThat(turnosEn("ESPERANDO") + turnosEn("ADMITIDO") + turnosEn("COMPLETADO")).isEqualTo(12);
    }
}
