package co.soundpass.vista;

import jakarta.validation.constraints.NotBlank;

public record LoginSolicitud(@NotBlank String correo, @NotBlank String contrasena) {
}
