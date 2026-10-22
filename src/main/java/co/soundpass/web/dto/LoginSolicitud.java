package co.soundpass.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginSolicitud(@NotBlank String correo, @NotBlank String contrasena) {
}