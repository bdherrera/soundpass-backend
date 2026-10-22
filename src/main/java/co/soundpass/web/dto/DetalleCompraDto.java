package co.soundpass.web.dto;

import co.soundpass.dominio.DetalleCompra;
import java.math.BigDecimal;

/** Una línea de la orden; se identifica por (orden, tipo de entrada), no tiene id propio. */
public record DetalleCompraDto(Integer idTipoEntrada, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {

    public static DetalleCompraDto de(DetalleCompra d) {
        BigDecimal subtotal = d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
        return new DetalleCompraDto(d.getTipoEntrada().getId(), d.getCantidad(), d.getPrecioUnitario(), subtotal);
    }
}