package co.soundpass.error;

import com.mongodb.MongoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduce todas las excepciones a la estructura única {@link ErrorApi}. */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorApi> negocio(ApiException ex, HttpServletRequest req) {
        return responder(ex.getEstado(), ex.getCodigo(), ex.getMessage(), req.getRequestURI(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorApi> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
            return responder(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                    "Se requiere autenticación", req.getRequestURI(), null);
        }
        return responder(HttpStatus.FORBIDDEN, CodigoError.PROHIBIDO,
                "No tienes permiso para esta operación", req.getRequestURI(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorApi> noAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                "Se requiere autenticación", req.getRequestURI(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorApi> validacion(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErrorApi.Detalle> detalles = ex.getConstraintViolations().stream()
                .map(v -> new ErrorApi.Detalle(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                "Datos de entrada inválidos", req.getRequestURI(), detalles);
    }

    /** Clave única repetida detectada por Spring (JDBC o MongoDB). */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ErrorApi> duplicado(DuplicateKeyException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, CodigoError.DUPLICADO,
                "Ya existe un registro con esos datos", req.getRequestURI(), null);
    }

    /** Una base de datos no responde: 503 en vez de un 500 genérico. */
    @ExceptionHandler({DataAccessResourceFailureException.class, MongoException.class})
    ResponseEntity<ErrorApi> servicioNoDisponible(Exception ex, HttpServletRequest req) {
        log.error("Base de datos no disponible en {}", req.getRequestURI(), ex);
        return responder(HttpStatus.SERVICE_UNAVAILABLE, CodigoError.ERROR_INTERNO,
                "El servicio no está disponible temporalmente", req.getRequestURI(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorApi> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        String sqlState = SqlEstado.de(ex);
        if (SqlEstado.EXCLUSION.equals(sqlState)) {
            return responder(HttpStatus.CONFLICT, CodigoError.SOLAPAMIENTO_ESCENARIO,
                    "El escenario ya tiene un evento en ese horario", req.getRequestURI(), null);
        }
        if (SqlEstado.UNICA.equals(sqlState)) {
            return responder(HttpStatus.CONFLICT, CodigoError.DUPLICADO,
                    "Ya existe un registro con esos datos", req.getRequestURI(), null);
        }
        if (sqlState != null && sqlState.startsWith("23")) {
            return responder(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                    "Los datos violan una restricción de la base de datos", req.getRequestURI(), null);
        }
        log.error("Error de integridad no clasificado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                "Error interno del servidor", req.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                "Error interno del servidor", req.getRequestURI(), null);
    }

    // ---- Excepciones propias de Spring MVC ----

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        HttpStatus estado = HttpStatus.valueOf(status.value());
        CodigoError codigo = estado == HttpStatus.NOT_FOUND ? CodigoError.NO_ENCONTRADO
                : estado.is4xxClientError() ? CodigoError.VALIDACION : CodigoError.ERROR_INTERNO;
        String mensaje = estado.is5xxServerError() ? "Error interno del servidor" : estado.getReasonPhrase();
        return cuerpo(estado, codigo, mensaje, request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorApi.Detalle> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorApi.Detalle(e.getField(), e.getDefaultMessage()))
                .toList();
        return cuerpo(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                "Datos de entrada inválidos", request, detalles);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return cuerpo(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                "Datos de entrada inválidos", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return cuerpo(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                "El cuerpo de la solicitud es inválido", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return cuerpo(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                "Falta el parámetro " + ex.getParameterName(), request, null);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return cuerpo(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION,
                "Valor inválido para " + ex.getPropertyName(), request, null);
    }

    private static ResponseEntity<ErrorApi> responder(HttpStatus estado, CodigoError codigo, String mensaje,
                                                      String ruta, List<ErrorApi.Detalle> detalles) {
        return ResponseEntity.status(estado).body(ErrorApi.de(estado, codigo, mensaje, ruta, detalles));
    }

    private static ResponseEntity<Object> cuerpo(HttpStatus estado, CodigoError codigo, String mensaje,
                                                 WebRequest request, List<ErrorApi.Detalle> detalles) {
        String ruta = ((ServletWebRequest) request).getRequest().getRequestURI();
        return ResponseEntity.status(estado).body(ErrorApi.de(estado, codigo, mensaje, ruta, detalles));
    }
}
