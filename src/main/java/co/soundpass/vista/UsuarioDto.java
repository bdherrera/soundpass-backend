package co.soundpass.vista;

import co.soundpass.modelo.Rol;
import co.soundpass.modelo.Usuario;

/** Vista pública del usuario: nunca incluye el hash de la contraseña. */
public record UsuarioDto(Integer idUsuario, String nombre, String correo, Rol rol) {

    public static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol());
    }
}
