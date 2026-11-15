package co.soundpass.error;

import org.springframework.http.HttpStatus;

/** Error de negocio con el estado HTTP y el código que se devolverán al cliente. */
public class ApiException extends RuntimeException {

    private final HttpStatus estado;
    private final CodigoError codigo;

    public ApiException(HttpStatus estado, CodigoError codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public HttpStatus getEstado() { return estado; }
    public CodigoError getCodigo() { return codigo; }

    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, CodigoError.NO_ENCONTRADO, mensaje);
    }

    public static ApiException validacion(String mensaje) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION, mensaje);
    }

    public static ApiException conflicto(CodigoError codigo, String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensaje);
    }

    public static ApiException noAutenticado(String mensaje) {
        return new ApiException(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO, mensaje);
    }

    public static ApiException prohibido(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, CodigoError.PROHIBIDO, mensaje);
    }
}
