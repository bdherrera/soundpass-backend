package co.soundpass.servicio.impl;

import co.soundpass.servicio.EventoServicio;
import static co.soundpass.modelo.EstadoEvento.BORRADOR;
import static co.soundpass.modelo.EstadoEvento.CANCELADO;
import static co.soundpass.modelo.EstadoEvento.FINALIZADO;
import static co.soundpass.modelo.EstadoEvento.PUBLICADO;

import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.modelo.Artista;
import co.soundpass.modelo.Escenario;
import co.soundpass.modelo.EstadoEvento;
import co.soundpass.modelo.Evento;
import co.soundpass.modelo.EventoArtista;
import co.soundpass.modelo.EventoArtistaId;
import co.soundpass.repositorio.ArtistaRepository;
import co.soundpass.repositorio.EscenarioRepository;
import co.soundpass.repositorio.EventoArtistaRepository;
import co.soundpass.repositorio.EventoRepository;
import co.soundpass.repositorio.TipoEntradaRepository;
import co.soundpass.seguridad.Sesion;
import co.soundpass.vista.ArtistaEnEventoDto;
import co.soundpass.vista.EscenarioDto;
import co.soundpass.vista.EventoDetalleDto;
import co.soundpass.vista.EventoDto;
import co.soundpass.vista.EventoEntrada;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.TipoEntradaDto;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventoServicioImpl implements EventoServicio {

    /** Transiciones permitidas del estado del evento. CANCELADO -> BORRADOR reactiva el evento. */
    private static final Map<EstadoEvento, Set<EstadoEvento>> TRANSICIONES = Map.of(
            BORRADOR, Set.of(PUBLICADO, CANCELADO),
            PUBLICADO, Set.of(CANCELADO, FINALIZADO),
            CANCELADO, Set.of(BORRADOR),
            FINALIZADO, Set.of());

    private final EventoRepository eventos;
    private final EscenarioRepository escenarios;
    private final ArtistaRepository artistas;
    private final EventoArtistaRepository eventoArtistas;
    private final TipoEntradaRepository tipos;

    public EventoServicioImpl(EventoRepository eventos, EscenarioRepository escenarios, ArtistaRepository artistas,
                          EventoArtistaRepository eventoArtistas, TipoEntradaRepository tipos) {
        this.eventos = eventos;
        this.escenarios = escenarios;
        this.artistas = artistas;
        this.eventoArtistas = eventoArtistas;
        this.tipos = tipos;
    }

    @Transactional(readOnly = true)
    @Override
    public Pagina<EventoDto> listarPublicados(OffsetDateTime desde, OffsetDateTime hasta, String ciudad,
                                              Integer idArtista, int pagina, int tamano) {
        Specification<Evento> filtro = (r, q, cb) -> cb.equal(r.get("estado"), PUBLICADO);
        if (desde != null) {
            filtro = filtro.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fechaInicio"), desde));
        }
        if (hasta != null) {
            filtro = filtro.and((r, q, cb) -> cb.lessThan(r.get("fechaInicio"), hasta));
        }
        if (ciudad != null && !ciudad.isBlank()) {
            String c = ciudad.trim().toLowerCase(Locale.ROOT);
            filtro = filtro.and((r, q, cb) -> cb.equal(cb.lower(r.join("escenario").get("ciudad")), c));
        }
        if (idArtista != null) {
            filtro = filtro.and((r, q, cb) -> {
                Subquery<Integer> sub = q.subquery(Integer.class);
                Root<EventoArtista> ea = sub.from(EventoArtista.class);
                sub.select(ea.get("id").get("idEvento")).where(
                        cb.equal(ea.get("id").get("idEvento"), r.get("id")),
                        cb.equal(ea.get("id").get("idArtista"), idArtista));
                return cb.exists(sub);
            });
        }
        var orden = Sort.by("fechaInicio", "id");
        return Pagina.de(eventos.findAll(filtro, PageRequest.of(pagina, tamano, orden)).map(EventoDto::de));
    }

    /** Un evento que no está PUBLICADO solo es visible para un ADMIN; para los demás no existe. */
    @Transactional(readOnly = true)
    @Override
    public Evento cargarVisible(Integer id) {
        Evento e = eventos.findById(id).orElseThrow(() -> ApiException.noEncontrado("El evento no existe"));
        if (e.getEstado() != PUBLICADO && !Sesion.esAdmin()) {
            throw ApiException.noEncontrado("El evento no existe");
        }
        return e;
    }

    @Transactional(readOnly = true)
    @Override
    public EventoDetalleDto obtener(Integer id) {
        Evento e = cargarVisible(id);
        List<ArtistaEnEventoDto> artistasEvento = eventoArtistas.deEvento(id).stream()
                .map(ea -> new ArtistaEnEventoDto(ea.getArtista().getId(), ea.getArtista().getNombreArtistico(),
                        ea.getOrdenPresentacion()))
                .toList();
        List<TipoEntradaDto> tiposEvento = tipos.findByEventoIdOrderById(id).stream().map(TipoEntradaDto::de).toList();
        return new EventoDetalleDto(e.getId(), e.getEscenario().getId(), e.getNombre(), e.getFechaInicio(),
                e.getFechaFin(), e.getEstado(), EscenarioDto.de(e.getEscenario()), artistasEvento, tiposEvento);
    }

    @Transactional
    @Override
    public EventoDto crear(EventoEntrada datos) {
        Evento e = new Evento();
        copiar(datos, e);
        return EventoDto.de(eventos.saveAndFlush(e));
    }

    @Transactional
    @Override
    public EventoDto actualizar(Integer id, EventoEntrada datos) {
        Evento e = buscar(id);
        copiar(datos, e);
        return EventoDto.de(eventos.saveAndFlush(e));
    }

    @Transactional
    @Override
    public EventoDto cambiarEstado(Integer id, EstadoEvento nuevo) {
        Evento e = buscar(id);
        if (!TRANSICIONES.get(e.getEstado()).contains(nuevo)) {
            throw ApiException.conflicto(CodigoError.TRANSICION_INVALIDA,
                    "No se puede pasar el evento de " + e.getEstado() + " a " + nuevo);
        }
        e.setEstado(nuevo);
        // flush: reactivar un evento cancelado puede chocar con otro del mismo escenario
        return EventoDto.de(eventos.saveAndFlush(e));
    }

    @Transactional
    @Override
    public ArtistaEnEventoDto asignarArtista(Integer idEvento, Integer idArtista, Integer orden) {
        Evento evento = buscar(idEvento);
        Artista artista = artistas.findByIdAndActivoTrue(idArtista)
                .orElseThrow(() -> ApiException.noEncontrado("El artista no existe"));
        EventoArtista ea = eventoArtistas.findById(new EventoArtistaId(idEvento, idArtista)).orElseGet(() -> {
            EventoArtista nuevo = new EventoArtista();
            nuevo.setEvento(evento);
            nuevo.setArtista(artista);
            return nuevo;
        });
        ea.setOrdenPresentacion(orden == null ? 1 : orden);
        eventoArtistas.saveAndFlush(ea);
        return new ArtistaEnEventoDto(artista.getId(), artista.getNombreArtistico(), ea.getOrdenPresentacion());
    }

    @Transactional
    @Override
    public void quitarArtista(Integer idEvento, Integer idArtista) {
        EventoArtista ea = eventoArtistas.findById(new EventoArtistaId(idEvento, idArtista))
                .orElseThrow(() -> ApiException.noEncontrado("El artista no está asignado al evento"));
        eventoArtistas.delete(ea);
    }

    private Evento buscar(Integer id) {
        return eventos.findById(id).orElseThrow(() -> ApiException.noEncontrado("El evento no existe"));
    }

    private void copiar(EventoEntrada datos, Evento e) {
        if (!datos.fechaFin().isAfter(datos.fechaInicio())) {
            throw ApiException.validacion("fechaFin debe ser posterior a fechaInicio");
        }
        Escenario escenario = escenarios.findByIdAndActivoTrue(datos.idEscenario())
                .orElseThrow(() -> ApiException.noEncontrado("El escenario no existe"));
        e.setEscenario(escenario);
        e.setNombre(datos.nombre().trim());
        e.setFechaInicio(datos.fechaInicio());
        e.setFechaFin(datos.fechaFin());
    }
}
