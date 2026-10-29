package co.soundpass.vista;

import co.soundpass.modelo.EstadoEvento;
import java.time.OffsetDateTime;
import java.util.List;

public record EventoDetalleDto(Integer idEvento, Integer idEscenario, String nombre, OffsetDateTime fechaInicio,
                               OffsetDateTime fechaFin, EstadoEvento estado, EscenarioDto escenario,
                               List<ArtistaEnEventoDto> artistas, List<TipoEntradaDto> tiposEntrada) {
}
