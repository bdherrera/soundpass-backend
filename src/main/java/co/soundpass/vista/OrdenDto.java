package co.soundpass.vista;

import co.soundpass.modelo.DetalleCompra;
import co.soundpass.modelo.EstadoOrden;
import co.soundpass.modelo.Orden;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record OrdenDto(Integer idOrden, OffsetDateTime fecha, EstadoOrden estado, BigDecimal total,
                       List<DetalleCompraDto> detalles) {

    public static OrdenDto de(Orden o, List<DetalleCompra> lineas, BigDecimal total) {
        List<DetalleCompraDto> detalles = lineas.stream()
                .sorted(Comparator.comparing((DetalleCompra d) -> d.getTipoEntrada().getId()))
                .map(DetalleCompraDto::de)
                .toList();
        return new OrdenDto(o.getId(), o.getFecha(), o.getEstado(), total, detalles);
    }
}
