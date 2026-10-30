package co.soundpass.controlador;

import co.soundpass.fachada.ColaFachada;
import co.soundpass.vista.ColaConfigEntrada;
import co.soundpass.vista.ColaEstadoDto;
import co.soundpass.vista.TurnoDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos/{idEvento}/cola")
public class ColaController {

    private final ColaFachada cola;

    public ColaController(ColaFachada cola) {
        this.cola = cola;
    }

    @GetMapping
    public ColaEstadoDto estado(@PathVariable Integer idEvento) {
        return cola.estado(idEvento);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ColaEstadoDto configurar(@PathVariable Integer idEvento, @Valid @RequestBody ColaConfigEntrada datos) {
        return cola.configurar(idEvento, datos);
    }

    /** Idempotente: si el usuario ya tiene un turno activo, lo devuelve. */
    @PostMapping("/turnos")
    public TurnoDto unirse(@PathVariable Integer idEvento) {
        return cola.unirse(idEvento);
    }

    @GetMapping("/turnos/mio")
    public TurnoDto miTurno(@PathVariable Integer idEvento) {
        return cola.miTurno(idEvento);
    }

    @DeleteMapping("/turnos/mio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salir(@PathVariable Integer idEvento) {
        cola.salir(idEvento);
    }
}
