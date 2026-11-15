package co.soundpass.fachada;

import co.soundpass.servicio.ColaServicio;
import co.soundpass.vista.ColaConfigEntrada;
import co.soundpass.vista.ColaEstadoDto;
import co.soundpass.vista.TurnoDto;
import org.springframework.stereotype.Component;

/** Cola de reserva de un evento: configuración (ADMIN) y turnos de los usuarios. */
@Component
public class ColaFachada {

    private final ColaServicio cola;

    public ColaFachada(ColaServicio cola) {
        this.cola = cola;
    }

    public ColaEstadoDto estado(Integer idEvento) {
        return cola.estado(idEvento);
    }

    public ColaEstadoDto configurar(Integer idEvento, ColaConfigEntrada datos) {
        return cola.configurar(idEvento, datos);
    }

    public TurnoDto unirse(Integer idEvento) {
        return cola.unirse(idEvento);
    }

    public TurnoDto miTurno(Integer idEvento) {
        return cola.miTurno(idEvento);
    }

    public void salir(Integer idEvento) {
        cola.salir(idEvento);
    }
}
