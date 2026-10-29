package co.soundpass.vista;

import co.soundpass.modelo.EstadoEvento;
import co.soundpass.modelo.Evento;
import java.time.OffsetDateTime;

public record EventoDto(Integer idEvento, Integer idEscenario, String nombre, OffsetDateTime fechaInicio,
                        OffsetDateTime fechaFin, EstadoEvento estado) {

    public static EventoDto de(Evento e) {
        return new EventoDto(e.getId(), e.getEscenario().getId(), e.getNombre(), e.getFechaInicio(),
                e.getFechaFin(), e.getEstado());
    }
}
