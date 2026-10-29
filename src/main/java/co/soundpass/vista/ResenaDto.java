package co.soundpass.vista;

import co.soundpass.modelo.Resena;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ResenaDto(String id, Integer idEvento, Integer idUsuario, String nombreUsuario, int calificacion,
                        String comentario, OffsetDateTime fecha) {

    public static ResenaDto de(Resena r) {
        return new ResenaDto(r.getId(), r.getIdEvento(), r.getIdUsuario(), r.getUsuario().getNombre(),
                r.getCalificacion(), r.getComentario(), r.getFecha().atOffset(ZoneOffset.UTC));
    }
}
