package co.soundpass.fachada;

import co.soundpass.modelo.EstadoEvento;
import co.soundpass.servicio.EventoServicio;
import co.soundpass.servicio.TipoEntradaServicio;
import co.soundpass.servicio.VentasServicio;
import co.soundpass.vista.ArtistaEnEventoDto;
import co.soundpass.vista.DisponibilidadDto;
import co.soundpass.vista.EventoDetalleDto;
import co.soundpass.vista.EventoDto;
import co.soundpass.vista.EventoEntrada;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResumenVentasDto;
import co.soundpass.vista.TipoEntradaDto;
import co.soundpass.vista.TipoEntradaEntrada;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Eventos, sus artistas, sus tipos de entrada y la disponibilidad. */
@Component
public class EventoFachada {

    private final EventoServicio eventos;
    private final TipoEntradaServicio tipos;
    private final VentasServicio ventas;

    public EventoFachada(EventoServicio eventos, TipoEntradaServicio tipos, VentasServicio ventas) {
        this.eventos = eventos;
        this.tipos = tipos;
        this.ventas = ventas;
    }

    public List<ResumenVentasDto> resumenVentas(Integer idEvento) {
        return ventas.resumen(idEvento);
    }

    public Pagina<EventoDto> listarEventos(OffsetDateTime desde, OffsetDateTime hasta, String ciudad,
                                           Integer idArtista, int pagina, int tamano) {
        return eventos.listarPublicados(desde, hasta, ciudad, idArtista, pagina, tamano);
    }

    public EventoDetalleDto obtenerEvento(Integer idEvento) {
        return eventos.obtener(idEvento);
    }

    public EventoDto crearEvento(EventoEntrada datos) {
        return eventos.crear(datos);
    }

    public EventoDto actualizarEvento(Integer idEvento, EventoEntrada datos) {
        return eventos.actualizar(idEvento, datos);
    }

    public EventoDto cambiarEstado(Integer idEvento, EstadoEvento estado) {
        return eventos.cambiarEstado(idEvento, estado);
    }

    public ArtistaEnEventoDto asignarArtista(Integer idEvento, Integer idArtista, Integer ordenPresentacion) {
        return eventos.asignarArtista(idEvento, idArtista, ordenPresentacion);
    }

    public void quitarArtista(Integer idEvento, Integer idArtista) {
        eventos.quitarArtista(idEvento, idArtista);
    }

    public List<TipoEntradaDto> listarTiposEntrada(Integer idEvento) {
        return tipos.listar(idEvento);
    }

    public TipoEntradaDto crearTipoEntrada(Integer idEvento, TipoEntradaEntrada datos) {
        return tipos.crear(idEvento, datos);
    }

    public TipoEntradaDto actualizarTipoEntrada(Integer idTipoEntrada, TipoEntradaEntrada datos) {
        return tipos.actualizar(idTipoEntrada, datos);
    }

    public List<DisponibilidadDto> consultarDisponibilidad(Integer idEvento) {
        return tipos.disponibilidad(idEvento);
    }
}
