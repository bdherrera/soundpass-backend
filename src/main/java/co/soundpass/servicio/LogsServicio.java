package co.soundpass.servicio;

import co.soundpass.vista.LogDto;
import java.time.Instant;
import java.util.List;

/** Logs de la aplicación en MongoDB (patrón Bucket). Escribir un log nunca debe retrasar ni romper una petición. */
public interface LogsServicio {

    record EntradaLog(Instant fecha, String nivel, String logger, String hilo, String mensaje) {
    }

    /** No bloquea: si la cola interna está llena, la entrada se descarta. */
    void encolar(EntradaLog entrada);

    /** Espera a que se guarden las entradas encoladas hasta ahora. Pensado para pruebas. */
    boolean esperarEscrituras(long maximoMs) throws InterruptedException;

    /** Entradas más recientes primero, entre {@code desde} y {@code hasta} (por omisión, las últimas 24 horas). */
    List<LogDto> consultar(String nivel, Instant desde, Instant hasta, int limite);
}
