package co.soundpass.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import co.soundpass.servicio.LogsServicio;
import java.time.Instant;

/**
 * Appender de Logback que envía a {@link LogsServicio} los logs de la aplicación (co.soundpass, desde INFO) y los
 * avisos y errores de cualquier librería. Ignora los logs del driver de MongoDB y de LogsServicio para no
 * generar un ciclo (escribir un log produciría otro log).
 */
public class AppenderLogsMongo extends AppenderBase<ILoggingEvent> {

    private static final int MAXIMO_MENSAJE = 2000;

    private final LogsServicio logs;

    public AppenderLogsMongo(LogsServicio logs) {
        this.logs = logs;
    }

    @Override
    protected void append(ILoggingEvent evento) {
        String logger = evento.getLoggerName();
        if (logger.startsWith("org.mongodb") || logger.equals(LogsServicio.class.getName())) {
            return;
        }
        int minimo = logger.startsWith("co.soundpass") ? Level.INFO_INT : Level.WARN_INT;
        if (evento.getLevel().toInt() < minimo) {
            return;
        }
        logs.encolar(new LogsServicio.EntradaLog(Instant.ofEpochMilli(evento.getTimeStamp()),
                evento.getLevel().toString(), logger, evento.getThreadName(), mensaje(evento)));
    }

    private static String mensaje(ILoggingEvent evento) {
        String mensaje = evento.getFormattedMessage();
        IThrowableProxy error = evento.getThrowableProxy();
        if (error != null) {
            mensaje += " | " + error.getClassName() + ": " + error.getMessage();
        }
        return mensaje.length() > MAXIMO_MENSAJE ? mensaje.substring(0, MAXIMO_MENSAJE) : mensaje;
    }
}
