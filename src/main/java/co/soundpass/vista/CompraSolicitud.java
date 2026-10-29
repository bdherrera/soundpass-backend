package co.soundpass.vista;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CompraSolicitud(
        @NotBlank @Size(max = 64) String claveIdempotencia,
        @NotEmpty List<@Valid LineaCompra> lineas) {
}
