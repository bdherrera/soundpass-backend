package co.soundpass.web;

import co.soundpass.servicio.TipoEntradaServicio;
import co.soundpass.web.dto.DisponibilidadDto;
import co.soundpass.web.dto.TipoEntradaDto;
import co.soundpass.web.dto.TipoEntradaEntrada;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class TipoEntradaController {

    private final TipoEntradaServicio tipos;

    public TipoEntradaController(TipoEntradaServicio tipos) {
        this.tipos = tipos;
    }

    @GetMapping("/eventos/{idEvento}/tipos-entrada")
    public List<TipoEntradaDto> listar(@PathVariable Integer idEvento) {
        return tipos.listar(idEvento);
    }

    @PostMapping("/eventos/{idEvento}/tipos-entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TipoEntradaDto crear(@PathVariable Integer idEvento, @Valid @RequestBody TipoEntradaEntrada datos) {
        return tipos.crear(idEvento, datos);
    }

    @PutMapping("/tipos-entrada/{idTipoEntrada}")
    @PreAuthorize("hasRole('ADMIN')")
    public TipoEntradaDto actualizar(@PathVariable Integer idTipoEntrada,
                                     @Valid @RequestBody TipoEntradaEntrada datos) {
        return tipos.actualizar(idTipoEntrada, datos);
    }

    @GetMapping("/eventos/{idEvento}/disponibilidad")
    public List<DisponibilidadDto> disponibilidad(@PathVariable Integer idEvento) {
        return tipos.disponibilidad(idEvento);
    }
}