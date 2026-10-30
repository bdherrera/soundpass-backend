package co.soundpass.servicio;

import co.soundpass.vista.ColaConfigEntrada;
import co.soundpass.vista.ColaEstadoDto;
import co.soundpass.vista.TurnoDto;
import java.util.Collection;

/** Cola de reserva de un evento (MongoDB, patrón Computed). */
public interface ColaServicio {

    /** Crea o actualiza la configuración de la cola de un evento. */
    ColaEstadoDto configurar(Integer idEvento, ColaConfigEntrada datos);

    ColaEstadoDto estado(Integer idEvento);

    /** Entra a la cola; es idempotente si el usuario ya tiene un turno activo. */
    TurnoDto unirse(Integer idEvento);

    TurnoDto miTurno(Integer idEvento);

    void salir(Integer idEvento);

    /** Exige un turno ADMITIDO si alguno de los eventos tiene la cola habilitada. */
    void verificarAcceso(Integer idUsuario, Collection<Integer> idsEventos);

    /** Marca como completado el turno usado en una compra y admite al siguiente. */
    void completar(Integer idUsuario, Collection<Integer> idsEventos);
}
