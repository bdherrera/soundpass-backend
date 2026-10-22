package co.soundpass.web.dto;

import co.soundpass.dominio.Rol;
import co.soundpass.dominio.Usuario;

/** Vista pública del usuario: nunca incluye el hash de la contraseña. */
public record UsuarioDto(Integer idUsuario, String nombre, String correo, Rol rol) {

    public static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol());
    }
}