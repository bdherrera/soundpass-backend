package co.soundpass.seguridad;

import co.soundpass.error.CodigoError;
import co.soundpass.error.ErrorApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Errores 401 y 403 producidos por la cadena de filtros, con la estructura estándar. */
@Component
public class RespuestaErrorSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public RespuestaErrorSeguridad(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        escribir(req, res, HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO, "Se requiere autenticación");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        escribir(req, res, HttpStatus.FORBIDDEN, CodigoError.PROHIBIDO, "No tienes permiso para esta operación");
    }

    private void escribir(HttpServletRequest req, HttpServletResponse res, HttpStatus estado,
                          CodigoError codigo, String mensaje) throws IOException {
        res.setStatus(estado.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ErrorApi.de(estado, codigo, mensaje, req.getRequestURI(), null));
    }
}
