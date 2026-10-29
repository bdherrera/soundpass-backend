package co.soundpass.seguridad;

import co.soundpass.error.ApiException;
import co.soundpass.modelo.Rol;
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

    /** Id del usuario autenticado; sin sesión, 401. */
    public static Integer idUsuarioActual() {
        return usuarioActual().orElseThrow(() -> ApiException.noAutenticado("Se requiere autenticación")).id();
    }

    public static boolean esAdmin() {
        return usuarioActual().map(u -> u.rol() == Rol.ADMIN).orElse(false);
    }
}
