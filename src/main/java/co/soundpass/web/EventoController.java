package co.soundpass.web;

import co.soundpass.servicio.EventoServicio;
import co.soundpass.web.dto.ArtistaEnEventoDto;
import co.soundpass.web.dto.AsignacionArtista;
import co.soundpass.web.dto.CambioEstadoEvento;
import co.soundpass.web.dto.EventoDetalleDto;
import co.soundpass.web.dto.EventoDto;
import co.soundpass.web.dto.EventoEntrada;
import co.soundpass.web.dto.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    private final EventoServicio eventos;

    public EventoController(EventoServicio eventos) {
        this.eventos = eventos;
    }

    @GetMapping
    public Pagina<EventoDto> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) Integer idArtista,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return eventos.listarPublicados(desde, hasta, ciudad, idArtista, pagina, tamano);
    }

    @GetMapping("/{idEvento}")
    public EventoDetalleDto obtener(@PathVariable Integer idEvento) {
        return eventos.obtener(idEvento);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EventoDto crear(@Valid @RequestBody EventoEntrada datos) {
        return eventos.crear(datos);
    }

    @PutMapping("/{idEvento}")
    @PreAuthorize("hasRole('ADMIN')")
    public EventoDto actualizar(@PathVariable Integer idEvento, @Valid @RequestBody EventoEntrada datos) {
        return eventos.actualizar(idEvento, datos);
    }

    @PatchMapping("/{idEvento}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public EventoDto cambiarEstado(@PathVariable Integer idEvento, @Valid @RequestBody CambioEstadoEvento cambio) {
        return eventos.cambiarEstado(idEvento, cambio.estado());
    }

    @PutMapping("/{idEvento}/artistas/{idArtista}")
    @PreAuthorize("hasRole('ADMIN')")
    public ArtistaEnEventoDto asignarArtista(@PathVariable Integer idEvento, @PathVariable Integer idArtista,
                                             @Valid @RequestBody AsignacionArtista asignacion) {
        return eventos.asignarArtista(idEvento, idArtista, asignacion.ordenPresentacion());
    }

    @DeleteMapping("/{idEvento}/artistas/{idArtista}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void quitarArtista(@PathVariable Integer idEvento, @PathVariable Integer idArtista) {
        eventos.quitarArtista(idEvento, idArtista);
    }
}