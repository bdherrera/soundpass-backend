package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.config.MongoEsquema;
import co.soundpass.config.RegistroLogsEnMongo;
import co.soundpass.servicio.LogsServicio;
import co.soundpass.servicio.LogsServicio.EntradaLog;
import co.soundpass.soporte.PruebaApiBase;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class LogsBucketTest extends PruebaApiBase {

    @Autowired LogsServicio logs;
    @Autowired RegistroLogsEnMongo registro;
    @Autowired MongoEsquema esquema;

    @BeforeEach
    void conectarElAppender() {
        // otro contexto de Spring de la misma JVM pudo quedarse con el appender del logger raíz
        registro.activar();
    }

    private void esperar() throws InterruptedException {
        assertThat(logs.esperarEscrituras(10_000)).as("se guardaron los logs encolados").isTrue();
    }

    private List<Document> buckets(Instant hora) {
        return mongo.getCollection(MongoEsquema.LOGS).find(Filters.eq("hora", Date.from(hora)))
                .sort(Sorts.ascending("_id")).into(new ArrayList<>());
    }

    private List<String> mensajes() {
        List<String> mensajes = new ArrayList<>();
        for (Document bucket : mongo.getCollection(MongoEsquema.LOGS).find()) {
            bucket.getList("entradas", Document.class).forEach(e -> mensajes.add(e.getString("mensaje")));
        }
        return mensajes;
    }

    // ------------------------------------------------------------- patrón Bucket

    @Test
    void agrupaLasEntradasEnBucketsDeCien() throws Exception {
        Instant hora = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 250; i++) {
            logs.encolar(new EntradaLog(hora.plusSeconds(15 + i), "INFO", "prueba.bucket", "t", "mensaje " + i));
        }
        esperar();

        List<Document> buckets = buckets(hora);
        assertThat(buckets).extracting(b -> b.getInteger("cantidad")).containsExactly(100, 100, 50);
        assertThat(buckets).allSatisfy(b -> assertThat(b.getList("entradas", Document.class))
                .hasSize(b.getInteger("cantidad")));
        assertThat(buckets.get(0).getList("entradas", Document.class).get(0).getString("mensaje"))
                .isEqualTo("mensaje 0");
        assertThat(buckets.get(2).getList("entradas", Document.class).get(49).getString("mensaje"))
                .isEqualTo("mensaje 249");
    }

    @Test
    void cadaHoraTieneSuPropioBucket() throws Exception {
        Instant diez = Instant.parse("2026-01-01T10:59:59Z");
        Instant once = Instant.parse("2026-01-01T11:00:00Z");
        logs.encolar(new EntradaLog(diez, "WARN", "prueba.bucket", "t", "fin de la hora"));
        logs.encolar(new EntradaLog(once, "WARN", "prueba.bucket", "t", "inicio de la hora"));
        esperar();

        assertThat(buckets(Instant.parse("2026-01-01T10:00:00Z"))).hasSize(1);
        assertThat(buckets(Instant.parse("2026-01-01T11:00:00Z"))).hasSize(1);
    }

    @Test
    void losBucketsVencenSolosPorElIndiceTtl() throws Exception {
        logs.encolar(new EntradaLog(Instant.parse("2026-01-01T10:00:00Z"), "INFO", "prueba.bucket", "t", "vence"));
        esperar();

        var ttl = mongo.indexOps(MongoEsquema.LOGS).getIndexInfo().stream()
                .filter(i -> i.getName().equals("ttl_logs_expira_en")).findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter()).contains(Duration.ZERO);
        assertThat(ttl.getIndexFields()).extracting(f -> f.getKey()).containsExactly("expira_en");
        Date expira = buckets(Instant.parse("2026-01-01T10:00:00Z")).get(0).getDate("expira_en");
        assertThat(Duration.between(Instant.now(), expira.toInstant()).toDays()).isBetween(28L, 30L);
    }

    @Test
    void elValidadorRechazaBucketsInvalidos() {
        esquema.asegurar();
        var coleccion = mongo.getCollection(MongoEsquema.LOGS);
        Document entrada = new Document("fecha", new Date()).append("nivel", "INFO").append("logger", "x")
                .append("mensaje", "ok");
        // hora fija en el pasado: los buckets que el escritor crea para los accesos de esta prueba son otros
        Date horaDePrueba = Date.from(Instant.parse("2020-01-01T00:00:00Z"));
        Document valido = new Document("hora", horaDePrueba).append("cantidad", 1).append("entradas", List.of(entrada))
                .append("expira_en", new Date()).append("version_esquema", 1);
        coleccion.insertOne(valido);

        Document nivelInvalido = new Document("hora", horaDePrueba).append("cantidad", 1)
                .append("entradas", List.of(new Document(entrada).append("nivel", "NOTICE")))
                .append("expira_en", new Date()).append("version_esquema", 1);
        assertThatThrownBy(() -> coleccion.insertOne(nivelInvalido)).isInstanceOf(MongoWriteException.class)
                .satisfies(e -> assertThat(((MongoWriteException) e).getCode()).isEqualTo(121));

        Document demasiado = new Document("hora", new Date()).append("cantidad", 101)
                .append("entradas", List.of(entrada)).append("expira_en", new Date()).append("version_esquema", 1);
        assertThatThrownBy(() -> coleccion.insertOne(demasiado)).isInstanceOf(MongoWriteException.class);
        Document sinVencimiento = new Document("hora", horaDePrueba).append("cantidad", 1)
                .append("entradas", List.of(entrada)).append("version_esquema", 1);
        assertThatThrownBy(() -> coleccion.insertOne(sinVencimiento)).isInstanceOf(MongoWriteException.class);
        assertThat(coleccion.countDocuments(Filters.eq("hora", horaDePrueba))).isEqualTo(1);
    }

    // ------------------------------------------------------------- el appender

    @Test
    void elAppenderGuardaLosLogsDeLaAplicacionYLosAvisosDeLasLibrerias() throws Exception {
        Logger propio = LoggerFactory.getLogger("co.soundpass.prueba.appender");
        Logger libreria = LoggerFactory.getLogger("org.libreria.externa");

        propio.info("propio-info");
        propio.debug("propio-debug");
        libreria.info("libreria-info");
        libreria.warn("libreria-aviso");
        libreria.error("libreria-error", new IllegalStateException("boom"));
        LoggerFactory.getLogger("org.mongodb.driver.prueba").warn("driver-ignorado");
        esperar();

        List<String> mensajes = mensajes();
        assertThat(mensajes).contains("propio-info", "libreria-aviso", "libreria-error | java.lang.IllegalStateException: boom");
        assertThat(mensajes).doesNotContain("propio-debug", "libreria-info", "driver-ignorado");
    }

    @Test
    void registraUnaLineaPorPeticionConSuUsuarioYSaltaElHealthCheck() throws Exception {
        int idAdmin = jdbc.queryForObject("select min(id_usuario) from usuario where rol = 'ADMIN'", Integer.class);

        mvc.perform(get("/api/v1/artistas").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ordenes/mias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health"));
        esperar();

        List<String> mensajes = mensajes();
        assertThat(mensajes).anyMatch(m -> m.matches("GET /api/v1/artistas 200 \\d+ms usuario=" + idAdmin));
        assertThat(mensajes).anyMatch(m -> m.matches("GET /api/v1/ordenes/mias 401 \\d+ms usuario=-"));
        assertThat(mensajes).noneMatch(m -> m.contains("/actuator"));
    }

    // ------------------------------------------------------------ consulta (API)

    @Test
    void soloAdminConsultaLosLogsYSePuedeFiltrar() throws Exception {
        Instant ahora = Instant.now();
        logs.encolar(new EntradaLog(ahora.minusSeconds(30), "ERROR", "co.soundpass.prueba.consulta", "t", "consulta-antiguo"));
        logs.encolar(new EntradaLog(ahora.minusSeconds(10), "ERROR", "co.soundpass.prueba.consulta", "t", "consulta-reciente"));
        logs.encolar(new EntradaLog(ahora.minusSeconds(20), "WARN", "co.soundpass.prueba.consulta", "t", "consulta-aviso"));
        esperar();

        mvc.perform(get("/api/v1/logs").param("nivel", "ERROR").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mensaje").value("consulta-reciente"))
                .andExpect(jsonPath("$[1].mensaje").value("consulta-antiguo"))
                .andExpect(jsonPath("$[0].nivel").value("ERROR"))
                .andExpect(jsonPath("$[0].logger").value("co.soundpass.prueba.consulta"))
                .andExpect(jsonPath("$[0].fecha").isNotEmpty());
        mvc.perform(get("/api/v1/logs").param("nivel", "ERROR").param("limite", "1").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mensaje").value("consulta-reciente"));
        mvc.perform(get("/api/v1/logs").param("nivel", "WARN").header("Authorization", admin))
                .andExpect(jsonPath("$[0].mensaje").value("consulta-aviso"));
        mvc.perform(get("/api/v1/logs").param("nivel", "ERROR")
                        .param("desde", ahora.minusSeconds(15).toString()).header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/logs").param("nivel", "ERROR")
                        .param("desde", ahora.plusSeconds(3600).toString()).header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void laConsultaDeLogsValidaPermisosYParametros() throws Exception {
        mvc.perform(get("/api/v1/logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/logs").header("Authorization", usuario)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/logs").param("nivel", "DEBUG").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/logs").param("limite", "0").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/logs").param("limite", "201").header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
    }
}
