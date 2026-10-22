package co.soundpass.web;

import co.soundpass.servicio.AuthServicio;
import co.soundpass.web.dto.LoginSolicitud;
import co.soundpass.web.dto.RegistroSolicitud;
import co.soundpass.web.dto.TokenRespuesta;
import co.soundpass.web.dto.UsuarioDto;
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

    private final AuthServicio auth;

    public AuthController(AuthServicio auth) {
        this.auth = auth;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioDto registrar(@Valid @RequestBody RegistroSolicitud solicitud) {
        return auth.registrar(solicitud);
    }

    @PostMapping("/login")
    TokenRespuesta iniciarSesion(@Valid @RequestBody LoginSolicitud solicitud) {
        return auth.iniciarSesion(solicitud);
    }
}