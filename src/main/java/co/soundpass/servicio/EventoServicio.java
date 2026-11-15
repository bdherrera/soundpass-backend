package co.soundpass.servicio;

import co.soundpass.modelo.EstadoEvento;
import co.soundpass.modelo.Evento;
import co.soundpass.vista.ArtistaEnEventoDto;
import co.soundpass.vista.EventoDetalleDto;
import co.soundpass.vista.EventoDto;
import co.soundpass.vista.EventoEntrada;
import co.soundpass.vista.Pagina;
import java.time.OffsetDateTime;

/** Eventos, su ciclo de estados y sus artistas. */
public interface EventoServicio {

    Pagina<EventoDto> listarPublicados(OffsetDateTime desde, OffsetDateTime hasta, String ciudad,
                                       Integer idArtista, int pagina, int tamano);

    /** Un evento que no está PUBLICADO solo es visible para un ADMIN; para los demás no existe. */
    Evento cargarVisible(Integer id);

    EventoDetalleDto obtener(Integer id);

    EventoDto crear(EventoEntrada datos);

    EventoDto actualizar(Integer id, EventoEntrada datos);

    /** Aplica solo las transiciones permitidas del estado del evento. */
    EventoDto cambiarEstado(Integer id, EstadoEvento nuevo);

    ArtistaEnEventoDto asignarArtista(Integer idEvento, Integer idArtista, Integer orden);

    void quitarArtista(Integer idEvento, Integer idArtista);
}
