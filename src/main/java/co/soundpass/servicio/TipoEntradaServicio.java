package co.soundpass.servicio;

import co.soundpass.dominio.EstadoEntrada;
import co.soundpass.dominio.Evento;
import co.soundpass.dominio.TipoEntrada;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.repositorio.EventoRepository;
import co.soundpass.repositorio.TipoEntradaRepository;
import co.soundpass.web.dto.DisponibilidadDto;
import co.soundpass.web.dto.TipoEntradaDto;
import co.soundpass.web.dto.TipoEntradaEntrada;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TipoEntradaServicio {

    private final TipoEntradaRepository tipos;
    private final EntradaRepository entradas;
    private final EventoRepository eventos;
    private final EventoServicio eventoServicio;

    public TipoEntradaServicio(TipoEntradaRepository tipos, EntradaRepository entradas, EventoRepository eventos,
                               EventoServicio eventoServicio) {
        this.tipos = tipos;
        this.entradas = entradas;
        this.eventos = eventos;
        this.eventoServicio = eventoServicio;
    }

    @Transactional(readOnly = true)
    public List<TipoEntradaDto> listar(Integer idEvento) {
        eventoServicio.cargarVisible(idEvento);
        return tipos.findByEventoIdOrderById(idEvento).stream().map(TipoEntradaDto::de).toList();
    }

    @Transactional(readOnly = true)
    public List<DisponibilidadDto> disponibilidad(Integer idEvento) {
        eventoServicio.cargarVisible(idEvento);
        return tipos.disponibilidad(idEvento);
    }

    /** Crea el tipo y, en la misma transacción, una entrada DISPONIBLE por cada unidad del cupo. */
    @Transactional
    public TipoEntradaDto crear(Integer idEvento, TipoEntradaEntrada datos) {
        Evento evento = eventos.findById(idEvento).orElseThrow(() -> ApiException.noEncontrado("El evento no existe"));
        TipoEntrada t = new TipoEntrada();
        t.setEvento(evento);
        copiar(datos, t);
        tipos.saveAndFlush(t);
        entradas.generar(t.getId(), t.getCupo());
        return TipoEntradaDto.de(t);
    }

    /**
     * El cupo es el total de entradas del tipo. Se puede subir (se generan más) o bajar hasta el número de
     * entradas ya no disponibles (se quitan entradas DISPONIBLE sin vender).
     */
    @Transactional
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
            entradas.generar(idTipo, (int) diferencia);
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