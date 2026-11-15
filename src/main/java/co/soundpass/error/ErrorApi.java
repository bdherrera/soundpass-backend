package co.soundpass.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Estructura única de error de toda la API (ver components.schemas.Error en docs/openapi.yaml). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorApi(int estado, String codigo, String mensaje, OffsetDateTime fecha, String ruta,
                       List<Detalle> detalles) {

    public record Detalle(String campo, String mensaje) {
    }

    public static ErrorApi de(HttpStatus estado, CodigoError codigo, String mensaje, String ruta,
                              List<Detalle> detalles) {
        return new ErrorApi(estado.value(), codigo.name(), mensaje, OffsetDateTime.now(), ruta, detalles);
    }
}
