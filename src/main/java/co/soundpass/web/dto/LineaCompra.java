package co.soundpass.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LineaCompra(@NotNull Integer idTipoEntrada, @NotNull @Min(1) Integer cantidad) {
}