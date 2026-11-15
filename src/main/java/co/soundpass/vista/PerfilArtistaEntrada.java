package co.soundpass.vista;

import co.soundpass.modelo.RutaRelativa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record PerfilArtistaEntrada(
        @Size(max = 5000) String biografia,
        @Size(max = 20) Map<@Pattern(regexp = "^[A-Za-z0-9_-]{1,30}$") String, @NotBlank @Size(max = 255) String> redes,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> enlaces,
        @Size(max = 50) List<@Pattern(regexp = RutaRelativa.PATRON) @Size(max = 255) String> galeria) {
}
