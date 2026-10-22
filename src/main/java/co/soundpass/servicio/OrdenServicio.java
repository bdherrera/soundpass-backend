package co.soundpass.servicio;

import co.soundpass.dominio.DetalleCompra;
import co.soundpass.dominio.Entrada;
import co.soundpass.dominio.EstadoEntrada;
import co.soundpass.dominio.EstadoOrden;
import co.soundpass.dominio.Orden;
import co.soundpass.dominio.OrdenTotal;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.repositorio.DetalleCompraRepository;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.repositorio.OrdenRepository;
import co.soundpass.repositorio.OrdenTotalRepository;
import co.soundpass.seguridad.Sesion;
import co.soundpass.web.dto.OrdenDto;
import co.soundpass.web.dto.Pagina;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdenServicio {

    private final OrdenRepository ordenes;
    private final DetalleCompraRepository detalles;
    private final OrdenTotalRepository totales;
    private final EntradaRepository entradas;

    public OrdenServicio(OrdenRepository ordenes, DetalleCompraRepository detalles, OrdenTotalRepository totales,
                         EntradaRepository entradas) {
        this.ordenes = ordenes;
        this.detalles = detalles;
        this.totales = totales;
        this.entradas = entradas;
    }

    @Transactional(readOnly = true)
    public Pagina<OrdenDto> misOrdenes(int pagina, int tamano) {
        Integer idUsuario = idUsuarioActual();
        var orden = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));
        var pagina1 = ordenes.findByUsuarioId(idUsuario, PageRequest.of(pagina, tamano, orden));
        List<OrdenDto> dtos = armar(pagina1.getContent());
        return new Pagina<>(dtos, pagina1.getNumber(), pagina1.getSize(), pagina1.getTotalElements(),
                pagina1.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrdenDto obtener(Integer idOrden) {
        Orden o = ordenes.findByIdAndUsuarioId(idOrden, idUsuarioActual()).orElseThrow(OrdenServicio::noExiste);
        return armar(List.of(o)).get(0);
    }

    /**
     * Cancela una orden propia. Las entradas VENDIDA pasan a CANCELADA y no vuelven al inventario
     * (la regla 5 no permite CANCELADA -> DISPONIBLE). Una orden PAGADA queda REEMBOLSADA; una PENDIENTE, CANCELADA.
     */
    @Transactional
    public OrdenDto cancelar(Integer idOrden) {
        Orden orden = ordenes.buscarPropiaParaActualizar(idOrden, idUsuarioActual())
                .orElseThrow(OrdenServicio::noExiste);
        if (orden.getEstado() != EstadoOrden.PAGADA && orden.getEstado() != EstadoOrden.PENDIENTE) {
            throw ApiException.conflicto(CodigoError.TRANSICION_INVALIDA,
                    "La orden ya está " + orden.getEstado());
        }
        List<Entrada> suyas = entradas.deOrdenParaActualizar(idOrden);
        if (suyas.stream().anyMatch(e -> e.getEstado() == EstadoEntrada.USADA)) {
            throw ApiException.conflicto(CodigoError.TRANSICION_INVALIDA,
                    "No se puede cancelar una orden con entradas ya usadas");
        }
        suyas.stream().filter(e -> e.getEstado() == EstadoEntrada.VENDIDA)
                .forEach(e -> e.setEstado(EstadoEntrada.CANCELADA));
        orden.setEstado(orden.getEstado() == EstadoOrden.PAGADA ? EstadoOrden.REEMBOLSADA : EstadoOrden.CANCELADA);
        ordenes.flush();
        return armar(List.of(orden)).get(0);
    }

    /** Arma los DTO con sus líneas y el total calculado por la vista v_orden_total. */
    List<OrdenDto> armar(List<Orden> lista) {
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

    static Integer idUsuarioActual() {
        return Sesion.usuarioActual().orElseThrow(() -> ApiException.noAutenticado("Se requiere autenticación")).id();
    }

    private static ApiException noExiste() {
        return ApiException.noEncontrado("La orden no existe");
    }
}