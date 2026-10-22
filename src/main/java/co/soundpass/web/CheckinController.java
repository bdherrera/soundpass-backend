package co.soundpass.web;

import co.soundpass.servicio.CheckinServicio;
import co.soundpass.web.dto.CheckinRespuesta;
import co.soundpass.web.dto.CheckinSolicitud;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkins")
public class CheckinController {

    private final CheckinServicio checkin;

    public CheckinController(CheckinServicio checkin) {
        this.checkin = checkin;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CheckinRespuesta hacer(@Valid @RequestBody CheckinSolicitud solicitud) {
        return checkin.hacer(solicitud.codigo());
    }
}