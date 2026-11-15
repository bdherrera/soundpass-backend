package co.soundpass.soporte;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints solo para pruebas: uno protegido y uno exclusivo de ADMIN. */
@RestController
@RequestMapping("/prueba")
public class PruebaController {

    @GetMapping("/protegido")
    String protegido() {
        return "ok";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String soloAdmin() {
        return "admin";
    }
}
