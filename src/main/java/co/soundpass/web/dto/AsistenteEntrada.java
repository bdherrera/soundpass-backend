package co.soundpass.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AsistenteEntrada(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 30) String documento,
        @Email @Size(max = 150) String correo) {
}