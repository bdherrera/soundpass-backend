package co.soundpass.vista;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code ventana}: personas que pueden estar comprando a la vez; {@code ttlMinutos}: tiempo que dura cada turno. */
public record ColaConfigEntrada(
        @NotNull Boolean habilitada,
        @NotNull @Min(1) @Max(1000) Integer ventana,
        @NotNull @Min(1) @Max(120) Integer ttlMinutos) {
}
