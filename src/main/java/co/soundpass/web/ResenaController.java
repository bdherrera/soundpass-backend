package co.soundpass.web;

import co.soundpass.servicio.ResenaServicio;
import co.soundpass.web.dto.Pagina;
import co.soundpass.web.dto.ResenaDto;
import co.soundpass.web.dto.ResenaEntrada;
import co.soundpass.web.dto.ResumenResenasDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos/{idEvento}/resenas")
public class ResenaController {

    private final ResenaServicio resenas;

    public ResenaController(ResenaServicio resenas) {
        this.resenas = resenas;
    }

    @GetMapping
    public Pagina<ResenaDto> listar(@PathVariable Integer idEvento,
                                    @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return resenas.listar(idEvento, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaDto crear(@PathVariable Integer idEvento, @Valid @RequestBody ResenaEntrada datos) {
        return resenas.crear(idEvento, datos);
    }

    @GetMapping("/resumen")
    public ResumenResenasDto resumen(@PathVariable Integer idEvento) {
        return resenas.resumen(idEvento);
    }
}