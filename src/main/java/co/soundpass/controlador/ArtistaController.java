package co.soundpass.controlador;

import co.soundpass.fachada.CatalogoFachada;
import co.soundpass.vista.ArtistaDto;
import co.soundpass.vista.ArtistaEntrada;
import co.soundpass.vista.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/artistas")
public class ArtistaController {

    private final CatalogoFachada catalogo;

    public ArtistaController(CatalogoFachada catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    public Pagina<ArtistaDto> listar(@RequestParam(required = false) String genero,
                                     @RequestParam(required = false) String pais,
                                     @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return catalogo.listarArtistas(genero, pais, pagina, tamano);
    }

    @GetMapping("/{idArtista}")
    public ArtistaDto obtener(@PathVariable Integer idArtista) {
        return catalogo.obtenerArtista(idArtista);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ArtistaDto crear(@Valid @RequestBody ArtistaEntrada datos) {
        return catalogo.crearArtista(datos);
    }

    @PutMapping("/{idArtista}")
    @PreAuthorize("hasRole('ADMIN')")
    public ArtistaDto actualizar(@PathVariable Integer idArtista, @Valid @RequestBody ArtistaEntrada datos) {
        return catalogo.actualizarArtista(idArtista, datos);
    }

    @DeleteMapping("/{idArtista}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void desactivar(@PathVariable Integer idArtista) {
        catalogo.desactivarArtista(idArtista);
    }
}
