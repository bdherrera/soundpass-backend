package co.soundpass.controlador;

import co.soundpass.fachada.ContenidoFachada;
import co.soundpass.vista.DetalleEventoDto;
import co.soundpass.vista.DetalleEventoEntrada;
import co.soundpass.vista.PerfilArtistaDto;
import co.soundpass.vista.PerfilArtistaEntrada;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PerfilController {

    private final ContenidoFachada contenido;

    public PerfilController(ContenidoFachada contenido) {
        this.contenido = contenido;
    }

    @GetMapping("/artistas/{idArtista}/perfil")
    public PerfilArtistaDto perfil(@PathVariable Integer idArtista) {
        return contenido.obtenerPerfilArtista(idArtista);
    }

    @PutMapping("/artistas/{idArtista}/perfil")
    @PreAuthorize("hasRole('ADMIN')")
    public PerfilArtistaDto guardarPerfil(@PathVariable Integer idArtista,
                                          @Valid @RequestBody PerfilArtistaEntrada datos) {
        return contenido.guardarPerfilArtista(idArtista, datos);
    }

    @GetMapping("/eventos/{idEvento}/detalle")
    public DetalleEventoDto detalle(@PathVariable Integer idEvento) {
        return contenido.obtenerDetalleEvento(idEvento);
    }

    @PutMapping("/eventos/{idEvento}/detalle")
    @PreAuthorize("hasRole('ADMIN')")
    public DetalleEventoDto guardarDetalle(@PathVariable Integer idEvento,
                                           @Valid @RequestBody DetalleEventoEntrada datos) {
        return contenido.guardarDetalleEvento(idEvento, datos);
    }
}
