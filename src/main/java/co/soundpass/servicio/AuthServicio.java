package co.soundpass.servicio;

import co.soundpass.vista.LoginSolicitud;
import co.soundpass.vista.RegistroSolicitud;
import co.soundpass.vista.TokenRespuesta;
import co.soundpass.vista.UsuarioDto;

/** Registro de usuarios e inicio de sesión. */
public interface AuthServicio {

    /** Crea un usuario con rol USUARIO; el correo debe ser único. */
    UsuarioDto registrar(RegistroSolicitud solicitud);

    /** Valida las credenciales y emite el JWT. */
    TokenRespuesta iniciarSesion(LoginSolicitud solicitud);
}
