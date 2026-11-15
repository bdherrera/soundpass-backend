package co.soundpass.servicio;

import co.soundpass.vista.CheckinRespuesta;
import java.util.UUID;

/** Check-in de entradas en la puerta del evento. */
public interface CheckinServicio {

    /** Pasa la entrada de VENDIDA a USADA; exige asistente registrado y evento PUBLICADO. */
    CheckinRespuesta hacer(UUID codigo);
}
