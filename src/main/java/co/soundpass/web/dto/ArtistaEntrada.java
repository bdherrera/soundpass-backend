package co.soundpass.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtistaEntrada(
        @NotBlank @Size(max = 100) String nombreArtistico,
        @Size(max = 60) String pais,
        @Size(max = 60) String genero) {
}