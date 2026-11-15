package co.soundpass.controlador;

import co.soundpass.fachada.EventoFachada;
import co.soundpass.vista.DisponibilidadDto;
import co.soundpass.vista.TipoEntradaDto;
import co.soundpass.vista.TipoEntradaEntrada;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class TipoEntradaController {

    private final EventoFachada eventos;

    public TipoEntradaController(EventoFachada eventos) {
        this.eventos = eventos;
    }

    @GetMapping("/eventos/{idEvento}/tipos-entrada")
    public List<TipoEntradaDto> listar(@PathVariable Integer idEvento) {
        return eventos.listarTiposEntrada(idEvento);
    }

    @PostMapping("/eventos/{idEvento}/tipos-entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TipoEntradaDto crear(@PathVariable Integer idEvento, @Valid @RequestBody TipoEntradaEntrada datos) {
        return eventos.crearTipoEntrada(idEvento, datos);
    }

    @PutMapping("/tipos-entrada/{idTipoEntrada}")
    @PreAuthorize("hasRole('ADMIN')")
    public TipoEntradaDto actualizar(@PathVariable Integer idTipoEntrada,
                                     @Valid @RequestBody TipoEntradaEntrada datos) {
        return eventos.actualizarTipoEntrada(idTipoEntrada, datos);
    }

    @GetMapping("/eventos/{idEvento}/disponibilidad")
    public List<DisponibilidadDto> disponibilidad(@PathVariable Integer idEvento) {
        return eventos.consultarDisponibilidad(idEvento);
    }
}
