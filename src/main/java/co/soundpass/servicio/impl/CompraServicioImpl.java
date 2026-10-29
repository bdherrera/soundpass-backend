package co.soundpass.servicio.impl;

import co.soundpass.servicio.CompraServicio;
import co.soundpass.servicio.OrdenServicio;
import co.soundpass.seguridad.Sesion;
import co.soundpass.modelo.Orden;
import co.soundpass.repositorio.OrdenRepository;
import co.soundpass.repositorio.ProcedimientosRepository;
import co.soundpass.repositorio.TipoEntradaRepository;
import co.soundpass.vista.CompraSolicitud;
import co.soundpass.vista.LineaCompra;
import co.soundpass.vista.ResultadoCompra;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompraServicioImpl implements CompraServicio {

    private final ProcedimientosRepository procedimientos;
    private final OrdenRepository ordenes;
    private final TipoEntradaRepository tipos;
    private final OrdenServicio ordenServicio;
    private final ObjectMapper mapper;

    public CompraServicioImpl(ProcedimientosRepository procedimientos, OrdenRepository ordenes,
                          TipoEntradaRepository tipos, OrdenServicio ordenServicio, ObjectMapper mapper) {
        this.procedimientos = procedimientos;
        this.ordenes = ordenes;
        this.tipos = tipos;
        this.ordenServicio = ordenServicio;
        this.mapper = mapper;
    }

    /**
     * Compra en una sola transacción con la función fn_comprar_entradas: reserva la clave de idempotencia, toma las
     * entradas con FOR UPDATE SKIP LOCKED, copia el precio vigente y las marca VENDIDA. Si falta alguna, la base de
     * datos lanza el error y se revierte todo. No hay pasarela de pago: la orden nace PAGADA.
     */
    @Transactional
    @Override
    public ResultadoCompra comprar(CompraSolicitud solicitud) {
        Integer idUsuario = Sesion.idUsuarioActual();
        var compra = procedimientos.comprar(idUsuario, solicitud.claveIdempotencia().trim(),
                lineasJson(solicitud.lineas()));
        Orden orden = ordenes.findById(compra.idOrden()).orElseThrow();
        return new ResultadoCompra(ordenServicio.armar(List.of(orden)).get(0), compra.creada());
    }

    /** ¿Ya existe una orden con esa clave? Una repetición idempotente no vuelve a pasar por la cola de reserva. */
    @Transactional(readOnly = true)
    @Override
    public boolean existeClave(String claveIdempotencia) {
        return ordenes.findByClaveIdempotencia(claveIdempotencia.trim()).isPresent();
    }

    /** Eventos de los tipos de entrada que pide la compra. */
    @Transactional(readOnly = true)
    @Override
    public List<Integer> eventosDe(CompraSolicitud solicitud) {
        return tipos.eventosDe(solicitud.lineas().stream().map(LineaCompra::idTipoEntrada).distinct().toList());
    }

    private String lineasJson(List<LineaCompra> lineas) {
        try {
            return mapper.writeValueAsString(lineas);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron serializar las líneas de compra", e);
        }
    }
}
