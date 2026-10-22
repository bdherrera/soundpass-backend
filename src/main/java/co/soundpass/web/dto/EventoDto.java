package co.soundpass.web.dto;

import co.soundpass.dominio.EstadoEvento;
import co.soundpass.dominio.Evento;
import java.time.OffsetDateTime;

public record EventoDto(Integer idEvento, Integer idEscenario, String nombre, OffsetDateTime fechaInicio,
                        OffsetDateTime fechaFin, EstadoEvento estado) {

    public static EventoDto de(Evento e) {
        return new EventoDto(e.getId(), e.getEscenario().getId(), e.getNombre(), e.getFechaInicio(),
                e.getFechaFin(), e.getEstado());
    }
}