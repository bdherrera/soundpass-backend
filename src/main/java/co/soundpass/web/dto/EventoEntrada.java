package co.soundpass.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record EventoEntrada(
        @NotNull Integer idEscenario,
        @NotBlank @Size(max = 150) String nombre,
        @NotNull OffsetDateTime fechaInicio,
        @NotNull OffsetDateTime fechaFin) {
}