package co.soundpass.seguridad;

import co.soundpass.dominio.Rol;

/** Identidad extraída del JWT; es el principal de la autenticación. */
public record UsuarioAutenticado(Integer id, String correo, Rol rol) {
}