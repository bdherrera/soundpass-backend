package co.soundpass.controlador;

import co.soundpass.fachada.CatalogoFachada;
import co.soundpass.vista.EscenarioDto;
import co.soundpass.vista.EscenarioEntrada;
import co.soundpass.vista.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/escenarios")
public class EscenarioController {

    private final CatalogoFachada catalogo;

    public EscenarioController(CatalogoFachada catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    public Pagina<EscenarioDto> listar(@RequestParam(required = false) String ciudad,
                                       @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return catalogo.listarEscenarios(ciudad, pagina, tamano);
    }

    @GetMapping("/{idEscenario}")
    public EscenarioDto obtener(@PathVariable Integer idEscenario) {
        return catalogo.obtenerEscenario(idEscenario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EscenarioDto crear(@Valid @RequestBody EscenarioEntrada datos) {
        return catalogo.crearEscenario(datos);
    }

    @PutMapping("/{idEscenario}")
    @PreAuthorize("hasRole('ADMIN')")
    public EscenarioDto actualizar(@PathVariable Integer idEscenario, @Valid @RequestBody EscenarioEntrada datos) {
        return catalogo.actualizarEscenario(idEscenario, datos);
    }

    @DeleteMapping("/{idEscenario}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void desactivar(@PathVariable Integer idEscenario) {
        catalogo.desactivarEscenario(idEscenario);
    }
}
