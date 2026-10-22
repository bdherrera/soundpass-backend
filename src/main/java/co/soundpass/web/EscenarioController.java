package co.soundpass.web;

import co.soundpass.servicio.EscenarioServicio;
import co.soundpass.web.dto.EscenarioDto;
import co.soundpass.web.dto.EscenarioEntrada;
import co.soundpass.web.dto.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/escenarios")
public class EscenarioController {

    private final EscenarioServicio escenarios;

    public EscenarioController(EscenarioServicio escenarios) {
        this.escenarios = escenarios;
    }

    @GetMapping
    public Pagina<EscenarioDto> listar(@RequestParam(required = false) String ciudad,
                                       @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return escenarios.listar(ciudad, pagina, tamano);
    }

    @GetMapping("/{idEscenario}")
    public EscenarioDto obtener(@PathVariable Integer idEscenario) {
        return escenarios.obtener(idEscenario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EscenarioDto crear(@Valid @RequestBody EscenarioEntrada datos) {
        return escenarios.crear(datos);
    }

    @PutMapping("/{idEscenario}")
    @PreAuthorize("hasRole('ADMIN')")
    public EscenarioDto actualizar(@PathVariable Integer idEscenario, @Valid @RequestBody EscenarioEntrada datos) {
        return escenarios.actualizar(idEscenario, datos);
    }

    @DeleteMapping("/{idEscenario}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void desactivar(@PathVariable Integer idEscenario) {
        escenarios.desactivar(idEscenario);
    }
}