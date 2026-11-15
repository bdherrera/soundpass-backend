package co.soundpass.vista;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TipoEntradaEntrada(
        @NotBlank @Size(max = 60) String nombre,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal precio,
        @NotNull @Min(1) @Max(100000) Integer cupo) {
}
