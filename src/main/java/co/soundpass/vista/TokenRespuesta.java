package co.soundpass.vista;

import java.time.OffsetDateTime;

public record TokenRespuesta(String token, String tipo, OffsetDateTime expiraEn, UsuarioDto usuario) {
}
