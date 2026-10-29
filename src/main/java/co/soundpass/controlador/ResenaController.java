package co.soundpass.controlador;

import co.soundpass.fachada.ContenidoFachada;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResenaDto;
import co.soundpass.vista.ResenaEntrada;
import co.soundpass.vista.ResumenResenasDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos/{idEvento}/resenas")
public class ResenaController {

    private final ContenidoFachada contenido;

    public ResenaController(ContenidoFachada contenido) {
        this.contenido = contenido;
    }

    @GetMapping
    public Pagina<ResenaDto> listar(@PathVariable Integer idEvento,
                                    @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return contenido.listarResenas(idEvento, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaDto crear(@PathVariable Integer idEvento, @Valid @RequestBody ResenaEntrada datos) {
        return contenido.crearResena(idEvento, datos);
    }

    @GetMapping("/resumen")
    public ResumenResenasDto resumen(@PathVariable Integer idEvento) {
        return contenido.resumenResenas(idEvento);
    }
}
