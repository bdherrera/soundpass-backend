package co.soundpass.fachada;

import co.soundpass.servicio.LogsServicio;
import co.soundpass.vista.LogDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Consulta de los logs de la aplicación guardados en MongoDB. */
@Component
public class LogFachada {

    private final LogsServicio logs;

    public LogFachada(LogsServicio logs) {
        this.logs = logs;
    }

    public List<LogDto> consultar(String nivel, OffsetDateTime desde, OffsetDateTime hasta, int limite) {
        return logs.consultar(nivel, desde == null ? null : desde.toInstant(),
                hasta == null ? null : hasta.toInstant(), limite);
    }
}
