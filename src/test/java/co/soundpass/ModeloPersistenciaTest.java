package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.soundpass.dominio.*;
import co.soundpass.repositorio.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Cada prueba corre en una transacción que se revierte al terminar. */
@SpringBootTest
@Transactional
class ModeloPersistenciaTest {

    @Autowired UsuarioRepository usuarios;
    @Autowired ArtistaRepository artistas;
    @Autowired EscenarioRepository escenarios;
    @Autowired EventoRepository eventos;
    @Autowired EventoArtistaRepository eventoArtistas;
    @Autowired TipoEntradaRepository tipos;
    @Autowired OrdenRepository ordenes;
    @Autowired DetalleCompraRepository detalles;
    @Autowired EntradaRepository entradas;
    @Autowired AsistenteRepository asistentes;
    @Autowired OrdenTotalRepository totales;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private final OffsetDateTime base = OffsetDateTime.now().plusDays(30).withNano(0);

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setNombre("Ana");
        u.setCorreo(UUID.randomUUID() + "@prueba.co");
        u.setContrasenaHash("hash");
        return usuarios.saveAndFlush(u);
    }

    private Escenario escenario() {
        Escenario e = new Escenario();
        e.setNombre("Arena");
        e.setCiudad("Bogotá");
        e.setCapacidad(5000);
        return escenarios.saveAndFlush(e);
    }

    private Evento evento(Escenario escenario, int horaInicio, int horaFin, EstadoEvento estado) {
        Evento ev = new Evento();
        ev.setEscenario(escenario);
        ev.setNombre("Concierto");
        ev.setFechaInicio(base.plusHours(horaInicio));
        ev.setFechaFin(base.plusHours(horaFin));
        ev.setEstado(estado);
        return eventos.saveAndFlush(ev);
    }

    private TipoEntrada tipo(Evento evento, String nombre, String precio) {
        TipoEntrada t = new TipoEntrada();
        t.setEvento(evento);
        t.setNombre(nombre);
        t.setPrecio(new BigDecimal(precio));
        t.setCupo(10);
        return tipos.saveAndFlush(t);
    }

    private Orden orden(Usuario usuario) {
        Orden o = new Orden();
        o.setUsuario(usuario);
        o.setClaveIdempotencia(UUID.randomUUID().toString());
        return ordenes.saveAndFlush(o);
    }

    private static String sqlState(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    @Test
    void guardaYLeeUsuarioArtistaYEscenario() {
        Usuario u = usuario();
        Artista a = new Artista();
        a.setNombreArtistico("Los Test");
        a.setPais("Colombia");
        artistas.saveAndFlush(a);
        Escenario e = escenario();
        em.clear();

        Usuario leido = usuarios.findByCorreo(u.getCorreo()).orElseThrow();
        assertThat(leido.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(leido.isActivo()).isTrue();
        assertThat(artistas.findById(a.getId()).orElseThrow().getNombreArtistico()).isEqualTo("Los Test");
        assertThat(escenarios.findById(e.getId()).orElseThrow().getCapacidad()).isEqualTo(5000);
    }

    @Test
    void guardaEventoConArtistaYTipoDeEntrada() {
        Evento ev = evento(escenario(), 0, 3, EstadoEvento.PUBLICADO);
        Artista a = artistas.saveAndFlush(nuevoArtista("Banda"));
        EventoArtista ea = new EventoArtista();
        ea.setEvento(ev);
        ea.setArtista(a);
        ea.setOrdenPresentacion(2);
        eventoArtistas.saveAndFlush(ea);
        TipoEntrada t = tipo(ev, "VIP", "150000.00");
        em.clear();

        EventoArtista leido = eventoArtistas.findById(new EventoArtistaId(ev.getId(), a.getId())).orElseThrow();
        assertThat(leido.getOrdenPresentacion()).isEqualTo(2);
        assertThat(tipos.findById(t.getId()).orElseThrow().getPrecio()).isEqualByComparingTo("150000.00");
        assertThat(eventos.findById(ev.getId()).orElseThrow().getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
    }

    @Test
    void detalleCompraUsaClaveCompuestaYSubtotalGenerado() {
        Orden o = orden(usuario());
        TipoEntrada t = tipo(evento(escenario(), 0, 3, EstadoEvento.PUBLICADO), "General", "100.00");

        DetalleCompra d = new DetalleCompra();
        d.setOrden(o);
        d.setTipoEntrada(t);
        d.setCantidad(3);
        d.setPrecioUnitario(new BigDecimal("100.00"));
        detalles.saveAndFlush(d);
        em.clear();

        DetalleCompra leido = detalles.findById(new DetalleCompraId(o.getId(), t.getId())).orElseThrow();
        assertThat(leido.getSubtotal()).isEqualByComparingTo("300.00");
        assertThat(totales.findById(o.getId()).orElseThrow().getTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void asistenteComparteLaClaveConSuEntrada() {
        Orden o = orden(usuario());
        TipoEntrada t = tipo(evento(escenario(), 0, 3, EstadoEvento.PUBLICADO), "General", "100.00");
        Entrada en = new Entrada();
        en.setTipoEntrada(t);
        en.setOrden(o);
        en.setEstado(EstadoEntrada.VENDIDA);
        entradas.saveAndFlush(en);

        Asistente as = new Asistente();
        as.setEntrada(en);
        as.setNombre("Luis");
        as.setDocumento("123");
        asistentes.saveAndFlush(as);
        em.clear();

        Asistente leido = asistentes.findById(en.getId()).orElseThrow();
        assertThat(leido.getId()).isEqualTo(en.getId());
        assertThat(leido.getEntrada().getCodigo()).isNotNull();
        assertThat(leido.getEntrada().getEstado()).isEqualTo(EstadoEntrada.VENDIDA);
    }

    @Test
    void eventoSolapadoEnElMismoEscenarioViolaLaExclusion() {
        Escenario esc = escenario();
        evento(esc, 0, 4, EstadoEvento.PUBLICADO);

        assertThatThrownBy(() -> evento(esc, 2, 6, EstadoEvento.BORRADOR))
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23P01"));
    }

    @Test
    void eventoAdyacenteNoSeConsideraSolapado() {
        Escenario esc = escenario();
        evento(esc, 0, 4, EstadoEvento.PUBLICADO);

        assertThatCode(() -> evento(esc, 4, 8, EstadoEvento.PUBLICADO)).doesNotThrowAnyException();
    }

    @Test
    void eventoCanceladoNoCuentaParaElSolapamiento() {
        Escenario esc = escenario();
        evento(esc, 0, 4, EstadoEvento.CANCELADO);

        assertThatCode(() -> evento(esc, 1, 3, EstadoEvento.PUBLICADO)).doesNotThrowAnyException();
    }

    @Test
    void repetirTipoDeEntradaEnLaMismaOrdenFallaPorLaClavePrimaria() {
        Orden o = orden(usuario());
        TipoEntrada t = tipo(evento(escenario(), 0, 3, EstadoEvento.PUBLICADO), "General", "100.00");
        DetalleCompra d = new DetalleCompra();
        d.setOrden(o);
        d.setTipoEntrada(t);
        d.setCantidad(1);
        d.setPrecioUnitario(new BigDecimal("100.00"));
        detalles.saveAndFlush(d);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO detalle_compra (id_orden, id_tipo_entrada, cantidad, precio_unitario) VALUES (?, ?, 2, 100)",
                o.getId(), t.getId()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private static Artista nuevoArtista(String nombre) {
        Artista a = new Artista();
        a.setNombreArtistico(nombre);
        return a;
    }
}
