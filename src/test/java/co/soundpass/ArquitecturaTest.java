package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hace cumplir MVC con Facade (sin levantar Spring):
 * controlador -> fachada -> servicio -> repositorio, y el modelo nunca sale por la API (solo la vista).
 */
class ArquitecturaTest {

    private static final String BASE = "co.soundpass";
    private static final String CONTROLADOR = BASE + ".controlador";
    private static final String FACHADA = BASE + ".fachada";
    private static final String SERVICIO = BASE + ".servicio";
    private static final String IMPLEMENTACION = SERVICIO + ".impl";
    private static final String MODELO = BASE + ".modelo";

    private static List<Class<?>> clases(String paquete, Class<? extends java.lang.annotation.Annotation> marca) {
        var escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(marca));
        List<Class<?>> clases = new ArrayList<>();
        for (var definicion : escaner.findCandidateComponents(paquete)) {
            try {
                clases.add(Class.forName(definicion.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return clases;
    }

    /** Tipos de los que depende una clase: parámetros de constructores y campos. */
    private static List<String> dependencias(Class<?> clase) {
        List<String> tipos = new ArrayList<>();
        Arrays.stream(clase.getDeclaredConstructors())
                .forEach(c -> Arrays.stream(c.getParameterTypes()).forEach(t -> tipos.add(t.getName())));
        for (Field campo : clase.getDeclaredFields()) {
            tipos.add(campo.getType().getName());
        }
        return tipos;
    }

    private static boolean esDeLaAplicacion(String tipo) {
        return tipo.startsWith(BASE + ".");
    }

    @Test
    void existenLosControladoresYLasFachadas() {
        assertThat(clases(CONTROLADOR, RestController.class)).hasSizeGreaterThanOrEqualTo(10);
        assertThat(clases(FACHADA, Component.class)).hasSizeGreaterThanOrEqualTo(6)
                .allSatisfy(c -> assertThat(c.getSimpleName()).endsWith("Fachada"));
    }

    @Test
    void todoControladorRestViveEnElPaqueteControlador() {
        var fueraDeSitio = clases(BASE, RestController.class).stream()
                .filter(c -> !c.getPackageName().equals(CONTROLADOR))
                .filter(c -> !c.getPackageName().startsWith(BASE + ".soporte")) // controladores solo de pruebas
                .toList();

        assertThat(fueraDeSitio).isEmpty();
    }

    @Test
    void losControladoresSoloDependenDeFachadas() {
        for (Class<?> controlador : clases(CONTROLADOR, RestController.class)) {
            var internas = dependencias(controlador).stream().filter(ArquitecturaTest::esDeLaAplicacion).toList();

            assertThat(internas).as(controlador.getSimpleName())
                    .allMatch(t -> t.startsWith(FACHADA + "."));
            assertThat(internas).as(controlador.getSimpleName() + " usa alguna fachada").isNotEmpty();
        }
    }

    /** Entidades y documentos nunca salen por la API; los enums del modelo sí son vocabulario compartido. */
    @Test
    void lasEntidadesDelModeloNoSeExponenPorLaApi() {
        var enumsDelModelo = Stream.of(co.soundpass.modelo.Rol.class, co.soundpass.modelo.EstadoEvento.class,
                co.soundpass.modelo.EstadoOrden.class, co.soundpass.modelo.EstadoEntrada.class)
                .map(Class::getName).toList();

        for (Class<?> controlador : clases(CONTROLADOR, RestController.class)) {
            for (Method metodo : controlador.getDeclaredMethods()) {
                var tipos = Stream.concat(Stream.of(metodo.getGenericReturnType()),
                        Arrays.stream(metodo.getGenericParameterTypes())).map(java.lang.reflect.Type::getTypeName)
                        .map(t -> enumsDelModelo.stream().reduce(t, (acc, e) -> acc.replace(e, "")));

                assertThat(tipos).as(controlador.getSimpleName() + "." + metodo.getName())
                        .noneMatch(t -> t.contains(MODELO + "."))
                        .noneMatch(t -> t.contains(SERVICIO + "."));
            }
        }
    }

    @Test
    void lasFachadasSoloDependenDeServicios() {
        for (Class<?> fachada : clases(FACHADA, Component.class)) {
            var internas = dependencias(fachada).stream().filter(ArquitecturaTest::esDeLaAplicacion).toList();

            assertThat(internas).as(fachada.getSimpleName()).allMatch(t -> t.startsWith(SERVICIO + "."));
            assertThat(internas).as(fachada.getSimpleName() + " usa algún servicio").isNotEmpty();
        }
    }

    /** Bajo acoplamiento: las fachadas y los demás servicios dependen de interfaces, nunca de implementaciones. */
    @Test
    void nadieDependeDeLasImplementacionesDeLosServicios() {
        var consumidores = new ArrayList<Class<?>>(clases(FACHADA, Component.class));
        consumidores.addAll(clases(SERVICIO, Service.class));

        for (Class<?> consumidor : consumidores) {
            assertThat(dependencias(consumidor)).as(consumidor.getSimpleName())
                    .noneMatch(t -> t.startsWith(IMPLEMENTACION + "."));
        }
    }

    @Test
    void cadaServicioTieneUnaInterfazYUnaImplementacion() {
        var implementaciones = clases(SERVICIO, Service.class);

        assertThat(implementaciones).hasSizeGreaterThanOrEqualTo(14);
        for (Class<?> impl : implementaciones) {
            assertThat(impl.getPackageName()).as(impl.getSimpleName()).isEqualTo(IMPLEMENTACION);
            assertThat(impl.getSimpleName()).endsWith("ServicioImpl");
            String nombreInterfaz = SERVICIO + "." + impl.getSimpleName().replaceFirst("Impl$", "");
            assertThat(impl.getInterfaces()).as(impl.getSimpleName())
                    .anyMatch(i -> i.getName().equals(nombreInterfaz) && i.isInterface());
        }
    }

    @Test
    void losServiciosNoDependenDeFachadasNiControladores() {
        var servicios = clases(SERVICIO, Service.class);

        assertThat(servicios).isNotEmpty();
        for (Class<?> servicio : servicios) {
            assertThat(dependencias(servicio)).as(servicio.getSimpleName())
                    .noneMatch(t -> t.startsWith(FACHADA + ".") || t.startsWith(CONTROLADOR + "."));
        }
    }

    @Test
    void elModeloNoDependeDeNingunaOtraCapa() {
        var capas = List.of(".controlador.", ".fachada.", ".servicio.", ".repositorio.", ".vista.");
        var modelo = Stream.concat(
                clases(MODELO, jakarta.persistence.Entity.class).stream(),
                clases(MODELO, org.springframework.data.mongodb.core.mapping.Document.class).stream()).toList();

        assertThat(modelo).isNotEmpty();
        for (Class<?> clase : modelo) {
            assertThat(dependencias(clase)).as(clase.getSimpleName())
                    .noneMatch(t -> capas.stream().anyMatch(c -> t.contains(BASE + c)));
        }
    }
}
