package co.soundpass.controlador;

import co.soundpass.fachada.AutenticacionFachada;
import co.soundpass.vista.LoginSolicitud;
import co.soundpass.vista.RegistroSolicitud;
import co.soundpass.vista.TokenRespuesta;
import co.soundpass.vista.UsuarioDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AutenticacionFachada autenticacion;

    public AuthController(AutenticacionFachada autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioDto registrar(@Valid @RequestBody RegistroSolicitud solicitud) {
        return autenticacion.registrar(solicitud);
    }

    @PostMapping("/login")
    TokenRespuesta iniciarSesion(@Valid @RequestBody LoginSolicitud solicitud) {
        return autenticacion.iniciarSesion(solicitud);
    }
}
