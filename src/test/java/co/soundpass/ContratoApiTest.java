package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;

import co.soundpass.soporte.PruebaApiBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Compara docs/openapi.yaml con lo implementado: mismas rutas y métodos, y el mismo nivel de acceso
 * (x-acceso: PUBLICO, USUARIO o ADMIN) comprobado con solicitudes reales.
 */
class ContratoApiTest extends PruebaApiBase {

    private static final String PREFIJO = "/api/v1";
    private static final List<String> METODOS = List.of("get", "post", "put", "patch", "delete");

    private record Operacion(HttpMethod metodo, String ruta, String acceso, boolean sinSeguridad) {
        String rutaConcreta() {
            return PREFIJO + ruta.replaceAll("\\{[^}]+}", "1");
        }

        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    private static List<Operacion> contrato;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeo;

    @SuppressWarnings("unchecked")
    @BeforeAll
    static void leerContrato() throws IOException {
        Map<String, Object> yaml;
        try (var lector = Files.newBufferedReader(Path.of("docs/openapi.yaml"))) {
            yaml = new Yaml().load(lector);
        }
        contrato = new ArrayList<>();
        for (var entrada : ((Map<String, Map<String, Object>>) yaml.get("paths")).entrySet()) {
            for (String metodo : METODOS) {
                Map<String, Object> op = (Map<String, Object>) entrada.getValue().get(metodo);
                if (op != null) {
                    boolean sinSeguridad = op.get("security") instanceof List<?> l && l.isEmpty();
                    contrato.add(new Operacion(HttpMethod.valueOf(metodo.toUpperCase()), entrada.getKey(),
                            (String) op.get("x-acceso"), sinSeguridad));
                }
            }
        }
    }

    private static String normalizar(String ruta) {
        return ruta.replaceAll("\\{[^}]+}", "{}");
    }

    @Test
    void lasRutasImplementadasSonLasDelContrato() {
        var enContrato = new TreeSet<String>();
        contrato.forEach(o -> enContrato.add(o.metodo() + " " + PREFIJO + normalizar(o.ruta())));

        var implementadas = new TreeSet<String>();
        mapeo.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType().isAnnotationPresent(RestController.class)
                    && handler.getBeanType().getPackageName().startsWith("co.soundpass.controlador")) {
                for (var metodo : info.getMethodsCondition().getMethods()) {
                    for (String patron : info.getPathPatternsCondition().getPatternValues()) {
                        implementadas.add(metodo.asHttpMethod() + " " + normalizar(patron));
                    }
                }
            }
        });

        assertThat(contrato).isNotEmpty();
        assertThat(implementadas).containsExactlyElementsOf(enContrato);
    }

    @Test
    void cadaOperacionDeclaraSuNivelDeAcceso() {
        for (Operacion op : contrato) {
            assertThat(op.acceso()).as(op.toString()).isIn("PUBLICO", "USUARIO", "ADMIN");
            assertThat(op.sinSeguridad()).as(op + " (security: [] solo en las públicas)")
                    .isEqualTo("PUBLICO".equals(op.acceso()));
        }
    }

    private MockHttpServletRequestBuilder solicitud(Operacion op, String bearer) {
        MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.request(op.metodo(), op.rutaConcreta());
        if (op.metodo() != HttpMethod.GET && op.metodo() != HttpMethod.DELETE) {
            peticion.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return bearer == null ? peticion : peticion.header("Authorization", bearer);
    }

    private int estado(Operacion op, String bearer) throws Exception {
        return mvc.perform(solicitud(op, bearer)).andReturn().getResponse().getStatus();
    }

    @Test
    void elAccesoImplementadoCoincideConElDelContrato() throws Exception {
        for (Operacion op : contrato) {
            switch (op.acceso()) {
                case "PUBLICO" -> assertThat(estado(op, null)).as(op + " sin token").isNotIn(401, 403);
                case "USUARIO" -> {
                    assertThat(estado(op, null)).as(op + " sin token").isEqualTo(401);
                    assertThat(estado(op, usuario)).as(op + " como USUARIO").isNotIn(401, 403);
                }
                case "ADMIN" -> {
                    assertThat(estado(op, null)).as(op + " sin token").isEqualTo(401);
                    assertThat(estado(op, usuario)).as(op + " como USUARIO").isEqualTo(403);
                }
                default -> throw new AssertionError("Acceso desconocido en " + op);
            }
        }
    }
}
