package co.soundpass.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Si la solicitud trae un JWT válido, autentica al usuario; si no, la deja anónima. */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    /** Atributo de la solicitud con el id del usuario autenticado; lo lee el log de accesos. */
    public static final String ATRIBUTO_USUARIO = "soundpass.usuario";

    private static final String PREFIJO = "Bearer ";

    private final JwtServicio jwt;

    public JwtFiltro(JwtServicio jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            jwt.validar(cabecera.substring(PREFIJO.length()).trim()).ifPresent(u -> {
                var autenticacion = new UsernamePasswordAuthenticationToken(u, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.rol().name())));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
                request.setAttribute(ATRIBUTO_USUARIO, u.id());
            });
        }
        chain.doFilter(request, response);
    }
}
