package co.soundpass.vista;

/** Estado de la cola de un evento; {@code enEspera} y {@code enCompra} salen de contadores precalculados. */
public record ColaEstadoDto(Integer idEvento, boolean habilitada, int ventana, int ttlMinutos, long enEspera,
                            long enCompra) {
}
