package co.soundpass.web.dto;

import co.soundpass.dominio.EstadoEntrada;
import java.time.OffsetDateTime;

public record CheckinRespuesta(Integer idEntrada, EstadoEntrada estado, OffsetDateTime fechaCheckin) {
}