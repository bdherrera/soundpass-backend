package co.soundpass.servicio.impl;

import co.soundpass.servicio.TipoEntradaServicio;
import co.soundpass.servicio.EventoServicio;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.modelo.EstadoEntrada;
import co.soundpass.modelo.Evento;
import co.soundpass.modelo.TipoEntrada;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.repositorio.EventoRepository;
import co.soundpass.repositorio.ProcedimientosRepository;
import co.soundpass.repositorio.TipoEntradaRepository;
import co.soundpass.vista.DisponibilidadDto;
import co.soundpass.vista.TipoEntradaDto;
import co.soundpass.vista.TipoEntradaEntrada;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TipoEntradaServicioImpl implements TipoEntradaServicio {

    private final TipoEntradaRepository tipos;
    private final EntradaRepository entradas;
    private final EventoRepository eventos;
    private final EventoServicio eventoServicio;
    private final ProcedimientosRepository procedimientos;

    public TipoEntradaServicioImpl(TipoEntradaRepository tipos, EntradaRepository entradas, EventoRepository eventos,
                               EventoServicio eventoServicio, ProcedimientosRepository procedimientos) {
        this.tipos = tipos;
        this.entradas = entradas;
        this.eventos = eventos;
        this.eventoServicio = eventoServicio;
        this.procedimientos = procedimientos;
    }

    @Transactional(readOnly = true)
    @Override
    public List<TipoEntradaDto> listar(Integer idEvento) {
        eventoServicio.cargarVisible(idEvento);
        return tipos.findByEventoIdOrderById(idEvento).stream().map(TipoEntradaDto::de).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<DisponibilidadDto> disponibilidad(Integer idEvento) {
        eventoServicio.cargarVisible(idEvento);
        return tipos.disponibilidad(idEvento);
    }

    /** Crea el tipo y, en la misma transacción, una entrada DISPONIBLE por cada unidad del cupo (sp_generar_entradas). */
    @Transactional
    @Override
    public TipoEntradaDto crear(Integer idEvento, TipoEntradaEntrada datos) {
        Evento evento = eventos.findById(idEvento).orElseThrow(() -> ApiException.noEncontrado("El evento no existe"));
        TipoEntrada t = new TipoEntrada();
        t.setEvento(evento);
        copiar(datos, t);
        tipos.saveAndFlush(t);
        procedimientos.generarEntradas(t.getId(), t.getCupo());
        return TipoEntradaDto.de(t);
    }

    /**
     * El cupo es el total de entradas del tipo. Se puede subir (se generan más) o bajar hasta el número de
     * entradas ya no disponibles (se quitan entradas DISPONIBLE sin vender).
     */
    @Transactional
    @Override
    public TipoEntradaDto actualizar(Integer idTipo, TipoEntradaEntrada datos) {
        TipoEntrada t = tipos.findById(idTipo)
                .orElseThrow(() -> ApiException.noEncontrado("El tipo de entrada no existe"));
        long total = entradas.countByTipoEntradaId(idTipo);
        long disponibles = entradas.countByTipoEntradaIdAndEstado(idTipo, EstadoEntrada.DISPONIBLE);
        long vendidas = total - disponibles;
        int nuevoCupo = datos.cupo();
        if (nuevoCupo < vendidas) {
            throw ApiException.conflicto(CodigoError.CUPO_INSUFICIENTE,
                    "El cupo no puede ser menor que las " + vendidas + " entradas ya vendidas");
        }
        copiar(datos, t);
        long diferencia = nuevoCupo - total;
        if (diferencia > 0) {
            procedimientos.generarEntradas(idTipo, (int) diferencia);
        } else if (diferencia < 0) {
            int sobrantes = (int) -diferencia;
            if (entradas.borrarDisponibles(idTipo, sobrantes) < sobrantes) {
                throw ApiException.conflicto(CodigoError.CUPO_INSUFICIENTE,
                        "Hay compras en curso; intenta reducir el cupo de nuevo");
            }
        }
        return TipoEntradaDto.de(tipos.saveAndFlush(t));
    }

    private static void copiar(TipoEntradaEntrada datos, TipoEntrada t) {
        t.setNombre(datos.nombre().trim());
        t.setPrecio(datos.precio());
        t.setCupo(datos.cupo());
    }
}
