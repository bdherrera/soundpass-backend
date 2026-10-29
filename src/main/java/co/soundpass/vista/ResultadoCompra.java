package co.soundpass.vista;

/** {@code creada} es false cuando la clave de idempotencia ya existía y se devolvió la orden original. */
public record ResultadoCompra(OrdenDto orden, boolean creada) {
}
