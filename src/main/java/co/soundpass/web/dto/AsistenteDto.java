package co.soundpass.web.dto;

import co.soundpass.dominio.Asistente;
import java.time.OffsetDateTime;

/** El asistente se identifica por el id de su entrada. */
public record AsistenteDto(Integer idEntrada, String nombre, String documento, String correo,
                           OffsetDateTime fechaCheckin) {

    public static AsistenteDto de(Asistente a) {
        return new AsistenteDto(a.getId(), a.getNombre(), a.getDocumento(), a.getCorreo(), a.getFechaCheckin());
    }
}