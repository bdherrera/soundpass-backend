package co.soundpass.seguridad;

import co.soundpass.dominio.Rol;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al usuario autenticado de la solicitud actual. */
public final class Sesion {

    private Sesion() {
    }

    public static Optional<UsuarioAutenticado> usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioAutenticado u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static boolean esAdmin() {
        return usuarioActual().map(u -> u.rol() == Rol.ADMIN).orElse(false);
    }
}