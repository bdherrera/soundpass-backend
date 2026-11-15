package co.soundpass.fachada;

import co.soundpass.servicio.AuthServicio;
import co.soundpass.vista.LoginSolicitud;
import co.soundpass.vista.RegistroSolicitud;
import co.soundpass.vista.TokenRespuesta;
import co.soundpass.vista.UsuarioDto;
import org.springframework.stereotype.Component;

/** Punto de entrada de los controladores para registro e inicio de sesión. */
@Component
public class AutenticacionFachada {

    private final AuthServicio auth;

    public AutenticacionFachada(AuthServicio auth) {
        this.auth = auth;
    }

    public UsuarioDto registrar(RegistroSolicitud solicitud) {
        return auth.registrar(solicitud);
    }

    public TokenRespuesta iniciarSesion(LoginSolicitud solicitud) {
        return auth.iniciarSesion(solicitud);
    }
}
