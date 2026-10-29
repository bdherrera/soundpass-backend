package co.soundpass.vista;

import java.math.BigDecimal;

/** Ventas de un tipo de entrada; {@code ingresos} suma solo las órdenes PAGADA (sin reembolsos ni cancelaciones). */
public record ResumenVentasDto(Integer idTipoEntrada, String nombre, int cupo, long disponibles, long vendidas,
                               long usadas, long canceladas, BigDecimal ingresos) {
}
