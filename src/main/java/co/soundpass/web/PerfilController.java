package co.soundpass.web;

import co.soundpass.servicio.PerfilServicio;
import co.soundpass.web.dto.DetalleEventoDto;
import co.soundpass.web.dto.DetalleEventoEntrada;
import co.soundpass.web.dto.PerfilArtistaDto;
import co.soundpass.web.dto.PerfilArtistaEntrada;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PerfilController {

    private final PerfilServicio perfiles;

    public PerfilController(PerfilServicio perfiles) {
        this.perfiles = perfiles;
    }

    @GetMapping("/artistas/{idArtista}/perfil")
    public PerfilArtistaDto perfil(@PathVariable Integer idArtista) {
        return perfiles.perfil(idArtista);
    }

    @PutMapping("/artistas/{idArtista}/perfil")
    @PreAuthorize("hasRole('ADMIN')")
    public PerfilArtistaDto guardarPerfil(@PathVariable Integer idArtista,
                                          @Valid @RequestBody PerfilArtistaEntrada datos) {
        return perfiles.guardarPerfil(idArtista, datos);
    }

    @GetMapping("/eventos/{idEvento}/detalle")
    public DetalleEventoDto detalle(@PathVariable Integer idEvento) {
        return perfiles.detalle(idEvento);
    }

    @PutMapping("/eventos/{idEvento}/detalle")
    @PreAuthorize("hasRole('ADMIN')")
    public DetalleEventoDto guardarDetalle(@PathVariable Integer idEvento,
                                           @Valid @RequestBody DetalleEventoEntrada datos) {
        return perfiles.guardarDetalle(idEvento, datos);
    }
}