package co.soundpass.controlador;

import co.soundpass.fachada.EntradaFachada;
import co.soundpass.modelo.EstadoEntrada;
import co.soundpass.vista.AsistenteDto;
import co.soundpass.vista.AsistenteEntrada;
import co.soundpass.vista.EntradaDto;
import co.soundpass.vista.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/entradas")
public class EntradaController {

    private final EntradaFachada entradas;

    public EntradaController(EntradaFachada entradas) {
        this.entradas = entradas;
    }

    @GetMapping("/mias")
    public Pagina<EntradaDto> mias(@RequestParam(required = false) EstadoEntrada estado,
                                   @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return entradas.misEntradas(estado, pagina, tamano);
    }

    @PutMapping("/{idEntrada}/asistente")
    @ResponseStatus(HttpStatus.CREATED)
    public AsistenteDto registrarAsistente(@PathVariable Integer idEntrada,
                                           @Valid @RequestBody AsistenteEntrada datos) {
        return entradas.registrarAsistente(idEntrada, datos);
    }
}
