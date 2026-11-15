package co.soundpass.vista;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EscenarioEntrada(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 80) String ciudad,
        @Size(max = 200) String direccion,
        @NotNull @Min(1) Integer capacidad) {
}
