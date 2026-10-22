package co.soundpass.web.dto;

import co.soundpass.dominio.EstadoEvento;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoEvento(@NotNull EstadoEvento estado) {
}