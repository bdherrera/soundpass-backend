package co.soundpass.servicio.impl;

import co.soundpass.servicio.CheckinServicio;
import co.soundpass.seguridad.Sesion;
import co.soundpass.modelo.EstadoEntrada;
import co.soundpass.repositorio.ProcedimientosRepository;
import co.soundpass.vista.CheckinRespuesta;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinServicioImpl implements CheckinServicio {

    private final ProcedimientosRepository procedimientos;

    public CheckinServicioImpl(ProcedimientosRepository procedimientos) {
        this.procedimientos = procedimientos;
    }

    /**
     * Check-in con la función fn_checkin: bloquea la entrada, exige que esté VENDIDA con asistente registrado y
     * evento PUBLICADO, y la pasa a USADA. Dos check-in simultáneos del mismo código se serializan en la base de datos.
     */
    @Transactional
    @Override
    public CheckinRespuesta hacer(UUID codigo) {
        var checkin = procedimientos.checkin(codigo, Sesion.idUsuarioActual());
        return new CheckinRespuesta(checkin.idEntrada(), EstadoEntrada.USADA, checkin.fecha());
    }
}
