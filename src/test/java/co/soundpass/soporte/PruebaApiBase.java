package co.soundpass.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.modelo.Rol;
import co.soundpass.modelo.Usuario;
import co.soundpass.repositorio.UsuarioRepository;
import co.soundpass.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Base de las pruebas de API contra la base de datos real. No es transaccional (un error de la API en una
 * transacción compartida la dejaría inservible), así que al terminar cada prueba vacía las tablas.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PruebaApiBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JwtServicio jwt;
    @Autowired protected org.springframework.data.mongodb.core.MongoTemplate mongo;

    protected ClienteApi cliente;
    protected String admin;
    protected String usuario;

    /** Punto de partida de las fechas de los eventos de la prueba. */
    protected final OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)
            .withMinute(0).withSecond(0).withNano(0);

    @BeforeEach
    void prepararBase() throws Exception {
        cliente = new ClienteApi(mvc, usuarios, encoder, mapper);
        admin = cliente.bearerDe(Rol.ADMIN);
        usuario = cliente.bearerDe(Rol.USUARIO);
    }

    @AfterEach
    void limpiarTablas() {
        jdbc.execute("TRUNCATE auditoria_estado, asistente, entrada, detalle_compra, orden, evento_artista, "
                + "tipo_entrada, evento, escenario, artista, usuario RESTART IDENTITY CASCADE");
        try {
            for (String coleccion : java.util.List.of("reseñas", "perfiles_artista", "detalle_evento",
                    "logs_aplicacion", "cola_evento", "turnos_cola")) {
                mongo.getCollection(coleccion).deleteMany(new org.bson.Document());
            }
        } catch (RuntimeException e) {
            // MongoDB puede estar apagado a propósito en la prueba
        }
    }

    protected String json(Object cuerpo) throws Exception {
        return mapper.writeValueAsString(cuerpo);
    }

    protected JsonNode cuerpo(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    protected static String unico(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** POST como ADMIN que debe responder 201; devuelve el cuerpo. */
    protected JsonNode crear(String ruta, Object datos) throws Exception {
        return cuerpo(mvc.perform(post(ruta).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(datos))).andExpect(status().isCreated()));
    }

    protected JsonNode crearEscenario(String ciudad) throws Exception {
        return crear("/api/v1/escenarios", Map.of("nombre", unico("Arena"), "ciudad", ciudad, "capacidad", 5000));
    }

    protected JsonNode crearArtista(String genero) throws Exception {
        return crear("/api/v1/artistas", Map.of("nombreArtistico", unico("Banda"), "pais", "Colombia", "genero", genero));
    }

    protected Map<String, Object> datosEvento(int idEscenario, int horaInicio, int horaFin) {
        return Map.of("idEscenario", idEscenario, "nombre", unico("Concierto"),
                "fechaInicio", base.plusHours(horaInicio).toString(), "fechaFin", base.plusHours(horaFin).toString());
    }

    protected JsonNode crearEvento(int idEscenario, int horaInicio, int horaFin) throws Exception {
        return crear("/api/v1/eventos", datosEvento(idEscenario, horaInicio, horaFin));
    }

    protected ResultActions cambiarEstado(int idEvento, String estado) throws Exception {
        return mvc.perform(patch("/api/v1/eventos/" + idEvento + "/estado").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("estado", estado))));
    }

    protected void publicar(int idEvento) throws Exception {
        cambiarEstado(idEvento, "PUBLICADO").andExpect(status().isOk());
    }

    /** Evento PUBLICADO en un escenario nuevo. */
    protected JsonNode eventoPublicado() throws Exception {
        JsonNode evento = crearEvento(crearEscenario(unico("Ciudad")).get("idEscenario").asInt(), 0, 3);
        publicar(evento.get("idEvento").asInt());
        return evento;
    }

    protected JsonNode crearTipoEntrada(int idEvento, String nombre, String precio, int cupo) throws Exception {
        return crear("/api/v1/eventos/" + idEvento + "/tipos-entrada",
                Map.of("nombre", nombre, "precio", new java.math.BigDecimal(precio), "cupo", cupo));
    }

    /** Un usuario con su cabecera Authorization (el token se emite directamente, sin pasar por BCrypt). */
    protected record Sesion(Usuario usuario, String bearer) {
    }

    protected Sesion nuevaSesion() {
        Usuario u = cliente.crearUsuario(Rol.USUARIO);
        return new Sesion(u, "Bearer " + jwt.emitir(u).token());
    }

    protected static Map<String, Integer> linea(int idTipoEntrada, int cantidad) {
        return Map.of("idTipoEntrada", idTipoEntrada, "cantidad", cantidad);
    }

    @SafeVarargs
    protected final ResultActions comprar(String bearer, String clave, Map<String, Integer>... lineas)
            throws Exception {
        return mvc.perform(post("/api/v1/ordenes").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("claveIdempotencia", clave, "lineas", java.util.List.of(lineas)))));
    }

    /** Compra que debe salir bien (201) y devuelve la orden. */
    @SafeVarargs
    protected final JsonNode comprarOk(String bearer, Map<String, Integer>... lineas) throws Exception {
        return cuerpo(comprar(bearer, unico("clave"), lineas).andExpect(status().isCreated()));
    }

    protected ResultActions entradasMias(String bearer, String... parametros) throws Exception {
        var peticion = get("/api/v1/entradas/mias").header("Authorization", bearer);
        for (int i = 0; i + 1 < parametros.length; i += 2) {
            peticion = peticion.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(peticion);
    }

    protected int contar(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Integer.class, argumentos);
    }

    protected record Respuesta(int estado, JsonNode cuerpo) {
        public String codigo() {
            return cuerpo.path("codigo").asText();
        }
    }

    protected Respuesta respuesta(ResultActions r) throws Exception {
        var res = r.andReturn().getResponse();
        String texto = res.getContentAsString();
        return new Respuesta(res.getStatus(), texto.isBlank() ? mapper.createObjectNode() : mapper.readTree(texto));
    }

    /** Ejecuta la tarea en {@code hilos} hilos que arrancan a la vez y devuelve sus resultados. */
    protected <T> java.util.List<T> enParalelo(int hilos, java.util.function.IntFunction<java.util.concurrent.Callable<T>> tareas)
            throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(hilos);
        try {
            var salida = new java.util.concurrent.CountDownLatch(1);
            var futuros = new java.util.ArrayList<java.util.concurrent.Future<T>>();
            for (int i = 0; i < hilos; i++) {
                var tarea = tareas.apply(i);
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            var resultados = new java.util.ArrayList<T>();
            for (var futuro : futuros) {
                resultados.add(futuro.get(90, java.util.concurrent.TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
