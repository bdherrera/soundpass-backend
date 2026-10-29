package co.soundpass.servicio;

import co.soundpass.vista.DisponibilidadDto;
import co.soundpass.vista.TipoEntradaDto;
import co.soundpass.vista.TipoEntradaEntrada;
import java.util.List;

/** Tipos de entrada de un evento y su inventario. */
public interface TipoEntradaServicio {

    List<TipoEntradaDto> listar(Integer idEvento);

    List<DisponibilidadDto> disponibilidad(Integer idEvento);

    /** Crea el tipo y una entrada DISPONIBLE por cada unidad del cupo. */
    TipoEntradaDto crear(Integer idEvento, TipoEntradaEntrada datos);

    /** El cupo es el total de entradas del tipo: puede subir, o bajar hasta las entradas ya no disponibles. */
    TipoEntradaDto actualizar(Integer idTipo, TipoEntradaEntrada datos);
}
