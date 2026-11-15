package co.soundpass.controlador;

import co.soundpass.fachada.EntradaFachada;
import co.soundpass.vista.CheckinRespuesta;
import co.soundpass.vista.CheckinSolicitud;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkins")
public class CheckinController {

    private final EntradaFachada entradas;

    public CheckinController(EntradaFachada entradas) {
        this.entradas = entradas;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CheckinRespuesta hacer(@Valid @RequestBody CheckinSolicitud solicitud) {
        return entradas.hacerCheckin(solicitud.codigo());
    }
}
