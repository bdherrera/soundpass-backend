package co.soundpass.config;

import co.soundpass.seguridad.JwtFiltro;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Una línea de log por petición (método, ruta, estado, duración y usuario); incluye las rechazadas por seguridad. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccesoLogFiltro extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("co.soundpass.acceso");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            Object usuario = request.getAttribute(JwtFiltro.ATRIBUTO_USUARIO);
            log.info("{} {} {} {}ms usuario={}", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    ms, usuario == null ? "-" : usuario);
        }
    }
}
