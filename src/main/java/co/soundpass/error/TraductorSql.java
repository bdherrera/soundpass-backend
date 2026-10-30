package co.soundpass.error;

import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

/**
 * Traduce los errores de negocio que lanzan los procedimientos y funciones de PostgreSQL
 * (RAISE EXCEPTION con SQLSTATE SP4xx, ver db/init/02_logica_en_base_de_datos.sql) a ApiException.
 */
public final class TraductorSql {

    private TraductorSql() {
    }

    /** Devuelve la ApiException equivalente, o la misma excepción si no es un error de negocio. */
    public static RuntimeException traducir(DataAccessException error) {
        String sqlState = SqlEstado.de(error);
        if (sqlState == null || !sqlState.startsWith("SP")) {
            return error;
        }
        String mensaje = mensaje(error);
        return switch (sqlState) {
            case "SP404" -> new ApiException(HttpStatus.NOT_FOUND, CodigoError.NO_ENCONTRADO, mensaje);
            case "SP409" -> new ApiException(HttpStatus.CONFLICT, CodigoError.TRANSICION_INVALIDA, mensaje);
            case "SP410" -> new ApiException(HttpStatus.CONFLICT, CodigoError.ENTRADAS_AGOTADAS, mensaje);
            case "SP411" -> new ApiException(HttpStatus.CONFLICT, CodigoError.DUPLICADO, mensaje);
            case "SP422" -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.VALIDACION, mensaje);
            default -> error;
        };
    }

    /** Primera línea del mensaje de PostgreSQL, sin el prefijo "ERROR:" ni el contexto PL/pgSQL. */
    private static String mensaje(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getMessage() != null) {
                String primera = sql.getMessage().lines().findFirst().orElse("");
                return primera.startsWith("ERROR: ") ? primera.substring("ERROR: ".length()) : primera;
            }
        }
        return "Operación rechazada";
    }
}
