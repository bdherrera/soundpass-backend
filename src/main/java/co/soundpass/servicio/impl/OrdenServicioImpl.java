package co.soundpass.servicio.impl;

import co.soundpass.servicio.OrdenServicio;
import co.soundpass.error.ApiException;
import co.soundpass.modelo.DetalleCompra;
import co.soundpass.modelo.Orden;
import co.soundpass.modelo.OrdenTotal;
import co.soundpass.repositorio.DetalleCompraRepository;
import co.soundpass.repositorio.OrdenRepository;
import co.soundpass.repositorio.OrdenTotalRepository;
import co.soundpass.repositorio.ProcedimientosRepository;
import co.soundpass.seguridad.Sesion;
import co.soundpass.vista.OrdenDto;
import co.soundpass.vista.Pagina;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdenServicioImpl implements OrdenServicio {

    private final OrdenRepository ordenes;
    private final DetalleCompraRepository detalles;
    private final OrdenTotalRepository totales;
    private final ProcedimientosRepository procedimientos;

    public OrdenServicioImpl(OrdenRepository ordenes, DetalleCompraRepository detalles, OrdenTotalRepository totales,
                         ProcedimientosRepository procedimientos) {
        this.ordenes = ordenes;
        this.detalles = detalles;
        this.totales = totales;
        this.procedimientos = procedimientos;
    }

    @Transactional(readOnly = true)
    @Override
    public Pagina<OrdenDto> misOrdenes(int pagina, int tamano) {
        Integer idUsuario = Sesion.idUsuarioActual();
        var orden = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));
        var pagina1 = ordenes.findByUsuarioId(idUsuario, PageRequest.of(pagina, tamano, orden));
        List<OrdenDto> dtos = armar(pagina1.getContent());
        return new Pagina<>(dtos, pagina1.getNumber(), pagina1.getSize(), pagina1.getTotalElements(),
                pagina1.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public OrdenDto obtener(Integer idOrden) {
        Orden o = ordenes.findByIdAndUsuarioId(idOrden, Sesion.idUsuarioActual()).orElseThrow(OrdenServicioImpl::noExiste);
        return armar(List.of(o)).get(0);
    }

    /**
     * Cancela una orden propia con el procedimiento sp_cancelar_orden: las entradas VENDIDA pasan a CANCELADA y
     * no vuelven al inventario (la regla 5 no permite CANCELADA -> DISPONIBLE). Una orden PAGADA queda
     * REEMBOLSADA; una PENDIENTE, CANCELADA.
     */
    @Transactional
    @Override
    public OrdenDto cancelar(Integer idOrden) {
        procedimientos.cancelarOrden(idOrden, Sesion.idUsuarioActual());
        Orden orden = ordenes.findById(idOrden).orElseThrow(OrdenServicioImpl::noExiste);
        return armar(List.of(orden)).get(0);
    }

    /** Arma los DTO con sus líneas y el total calculado por la vista v_orden_total. */
    @Override
    public List<OrdenDto> armar(List<Orden> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = lista.stream().map(Orden::getId).toList();
        Map<Integer, List<DetalleCompra>> lineas = detalles.findByOrdenIdIn(ids).stream()
                .collect(Collectors.groupingBy(d -> d.getOrden().getId()));
        Map<Integer, BigDecimal> total = totales.findAllById(ids).stream()
                .collect(Collectors.toMap(OrdenTotal::getIdOrden, OrdenTotal::getTotal));
        return lista.stream()
                .map(o -> OrdenDto.de(o, lineas.getOrDefault(o.getId(), List.of()),
                        total.getOrDefault(o.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private static ApiException noExiste() {
        return ApiException.noEncontrado("La orden no existe");
    }
}
