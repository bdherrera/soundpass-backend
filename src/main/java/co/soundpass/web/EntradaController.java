package co.soundpass.web;

import co.soundpass.dominio.EstadoEntrada;
import co.soundpass.servicio.EntradaServicio;
import co.soundpass.web.dto.AsistenteDto;
import co.soundpass.web.dto.AsistenteEntrada;
import co.soundpass.web.dto.EntradaDto;
import co.soundpass.web.dto.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/entradas")
public class EntradaController {

    private final EntradaServicio entradas;

    public EntradaController(EntradaServicio entradas) {
        this.entradas = entradas;
    }

    @GetMapping("/mias")
    public Pagina<EntradaDto> mias(@RequestParam(required = false) EstadoEntrada estado,
                                   @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return entradas.mias(estado, pagina, tamano);
    }

    @PutMapping("/{idEntrada}/asistente")
    @ResponseStatus(HttpStatus.CREATED)
    public AsistenteDto registrarAsistente(@PathVariable Integer idEntrada,
                                           @Valid @RequestBody AsistenteEntrada datos) {
        return entradas.registrarAsistente(idEntrada, datos);
    }
}