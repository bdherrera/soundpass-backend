package co.soundpass.vista;

import java.time.OffsetDateTime;

/**
 * Turno de un usuario en la cola. {@code posicion} es cuántos turnos faltan para ser admitido (0 si ya está
 * admitido o ya terminó); {@code expiraEn} solo viene cuando el turno está ADMITIDO.
 */
public record TurnoDto(Integer idEvento, long turno, String estado, long posicion, OffsetDateTime expiraEn) {
}
