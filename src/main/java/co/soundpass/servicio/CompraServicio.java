package co.soundpass.servicio;

import co.soundpass.dominio.DetalleCompra;
import co.soundpass.dominio.EstadoEvento;
import co.soundpass.dominio.Orden;
import co.soundpass.dominio.TipoEntrada;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.repositorio.DetalleCompraRepository;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.repositorio.OrdenRepository;
import co.soundpass.repositorio.TipoEntradaRepository;
import co.soundpass.web.dto.CompraSolicitud;
import co.soundpass.web.dto.LineaCompra;
import co.soundpass.web.dto.OrdenDto;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompraServicio {

    /** {@code creada} es false cuando la clave de idempotencia ya existía y se devolvió la orden original. */
    public record Resultado(OrdenDto orden, boolean creada) {
    }

    private final OrdenRepository ordenes;
    private final TipoEntradaRepository tipos;
    private final EntradaRepository entradas;
    private final DetalleCompraRepository detalles;
    private final OrdenServicio ordenServicio;

    public CompraServicio(OrdenRepository ordenes, TipoEntradaRepository tipos, EntradaRepository entradas,
                          DetalleCompraRepository detalles, OrdenServicio ordenServicio) {
        this.ordenes = ordenes;
        this.tipos = tipos;
        this.entradas = entradas;
        this.detalles = detalles;
        this.ordenServicio = ordenServicio;
    }

    /**
     * Compra en una sola transacción: reserva la clave de idempotencia, toma las entradas con
     * FOR UPDATE SKIP LOCKED, copia el precio vigente y las marca VENDIDA. Si falta alguna, se revierte todo.
     * No hay pasarela de pago: la orden nace PAGADA.
     */
    @Transactional
    public Resultado comprar(CompraSolicitud solicitud) {
        Integer idUsuario = OrdenServicio.idUsuarioActual();
        List<LineaCompra> lineas = ordenarSinRepetidos(solicitud.lineas());
        String clave = solicitud.claveIdempotencia().trim();

        Orden previa = ordenes.findByClaveIdempotencia(clave).orElse(null);
        if (previa != null) {
            return existente(previa, idUsuario);
        }
        Integer idOrden = ordenes.crearSiNoExiste(idUsuario, clave);
        if (idOrden == null) {
            // otra solicitud con la misma clave terminó primero
            return existente(ordenes.findByClaveIdempotencia(clave).orElseThrow(), idUsuario);
        }

        Orden orden = ordenes.findById(idOrden).orElseThrow();
        for (LineaCompra linea : lineas) {
            TipoEntrada tipo = tipos.findById(linea.idTipoEntrada())
                    .filter(t -> t.getEvento().getEstado() == EstadoEvento.PUBLICADO)
                    .orElseThrow(() -> ApiException.noEncontrado(
                            "El tipo de entrada " + linea.idTipoEntrada() + " no existe"));
            List<Integer> ids = entradas.bloquearDisponibles(tipo.getId(), linea.cantidad());
            if (ids.size() < linea.cantidad()) {
                throw ApiException.conflicto(CodigoError.ENTRADAS_AGOTADAS,
                        "No hay suficientes entradas disponibles de " + tipo.getNombre());
            }
            DetalleCompra detalle = new DetalleCompra();
            detalle.setOrden(orden);
            detalle.setTipoEntrada(tipo);
            detalle.setCantidad(linea.cantidad());
            detalle.setPrecioUnitario(tipo.getPrecio()); // precio histórico
            orden.getDetalles().add(detalle);
            detalles.save(detalle);
            if (entradas.marcarVendidas(idOrden, ids) != ids.size()) {
                throw new IllegalStateException("Entradas bloqueadas que dejaron de estar disponibles");
            }
        }
        detalles.flush();
        return new Resultado(ordenServicio.armar(List.of(orden)).get(0), true);
    }

    private Resultado existente(Orden orden, Integer idUsuario) {
        if (!orden.getUsuario().getId().equals(idUsuario)) {
            throw ApiException.conflicto(CodigoError.DUPLICADO, "La clave de idempotencia ya está en uso");
        }
        return new Resultado(ordenServicio.armar(List.of(orden)).get(0), false);
    }

    /** Una orden no repite el tipo de entrada. Se procesan en orden de id para mantener un orden estable. */
    private static List<LineaCompra> ordenarSinRepetidos(List<LineaCompra> lineas) {
        Set<Integer> vistos = new HashSet<>();
        for (LineaCompra l : lineas) {
            if (!vistos.add(l.idTipoEntrada())) {
                throw ApiException.validacion("El tipo de entrada " + l.idTipoEntrada() + " está repetido");
            }
        }
        return lineas.stream().sorted(Comparator.comparing(LineaCompra::idTipoEntrada)).toList();
    }
}