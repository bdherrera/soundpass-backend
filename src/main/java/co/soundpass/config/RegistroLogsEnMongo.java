package co.soundpass.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import co.soundpass.servicio.LogsServicio;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Conecta {@link AppenderLogsMongo} al logger raíz al arrancar y lo desconecta al apagar. */
@Component
public class RegistroLogsEnMongo {

    private static final String NOMBRE = "MONGO_BUCKETS";

    private final LogsServicio logs;
    private AppenderLogsMongo appender;

    public RegistroLogsEnMongo(LogsServicio logs) {
        this.logs = logs;
    }

    /** Idempotente: reemplaza un appender anterior con el mismo nombre (por ejemplo, de otro contexto de Spring). */
    @PostConstruct
    public synchronized void activar() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger raiz = contexto.getLogger(Logger.ROOT_LOGGER_NAME);
        var anterior = raiz.getAppender(NOMBRE);
        if (anterior != null) {
            raiz.detachAppender(anterior);
            anterior.stop();
        }
        appender = new AppenderLogsMongo(logs);
        appender.setContext(contexto);
        appender.setName(NOMBRE);
        appender.start();
        raiz.addAppender(appender);
    }

    @PreDestroy
    public synchronized void desactivar() {
        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger raiz = contexto.getLogger(Logger.ROOT_LOGGER_NAME);
        if (appender != null && raiz.getAppender(NOMBRE) == appender) {
            raiz.detachAppender(appender);
        }
        if (appender != null) {
            appender.stop();
        }
    }
}
