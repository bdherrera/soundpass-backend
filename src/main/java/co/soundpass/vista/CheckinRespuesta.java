package co.soundpass.vista;

import co.soundpass.modelo.EstadoEntrada;
import java.time.OffsetDateTime;

public record CheckinRespuesta(Integer idEntrada, EstadoEntrada estado, OffsetDateTime fechaCheckin) {
}
