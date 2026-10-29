package co.soundpass.vista;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResenaEntrada(@NotNull @Min(1) @Max(5) Integer calificacion, @Size(max = 2000) String comentario) {
}
