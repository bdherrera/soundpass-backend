package co.soundpass.web.dto;

import co.soundpass.mongo.RutaRelativa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DetalleEventoEntrada(
        @Size(max = 5000) String descripcion,
        @Size(max = 30) List<@NotBlank @Size(max = 50) String> etiquetas,
        @Size(max = 5000) String politicas,
        @Size(max = 50) List<@Pattern(regexp = RutaRelativa.PATRON) @Size(max = 255) String> medios) {
}