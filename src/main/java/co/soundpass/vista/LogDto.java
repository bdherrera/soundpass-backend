package co.soundpass.vista;

import java.time.OffsetDateTime;

public record LogDto(OffsetDateTime fecha, String nivel, String logger, String hilo, String mensaje) {
}
