package co.soundpass.web;

import co.soundpass.servicio.ArtistaServicio;
import co.soundpass.web.dto.ArtistaDto;
import co.soundpass.web.dto.ArtistaEntrada;
import co.soundpass.web.dto.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/artistas")
public class ArtistaController {

    private final ArtistaServicio artistas;

    public ArtistaController(ArtistaServicio artistas) {
        this.artistas = artistas;
    }

    @GetMapping
    public Pagina<ArtistaDto> listar(@RequestParam(required = false) String genero,
                                     @RequestParam(required = false) String pais,
                                     @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return artistas.listar(genero, pais, pagina, tamano);
    }

    @GetMapping("/{idArtista}")
    public ArtistaDto obtener(@PathVariable Integer idArtista) {
        return artistas.obtener(idArtista);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ArtistaDto crear(@Valid @RequestBody ArtistaEntrada datos) {
        return artistas.crear(datos);
    }

    @PutMapping("/{idArtista}")
    @PreAuthorize("hasRole('ADMIN')")
    public ArtistaDto actualizar(@PathVariable Integer idArtista, @Valid @RequestBody ArtistaEntrada datos) {
        return artistas.actualizar(idArtista, datos);
    }

    @DeleteMapping("/{idArtista}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void desactivar(@PathVariable Integer idArtista) {
        artistas.desactivar(idArtista);
    }
}