package co.soundpass.vista;

import co.soundpass.modelo.EstadoEvento;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoEvento(@NotNull EstadoEvento estado) {
}
