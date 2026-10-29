package co.soundpass.controlador;

import co.soundpass.fachada.EventoFachada;
import co.soundpass.vista.ArtistaEnEventoDto;
import co.soundpass.vista.AsignacionArtista;
import co.soundpass.vista.CambioEstadoEvento;
import co.soundpass.vista.EventoDetalleDto;
import co.soundpass.vista.EventoDto;
import co.soundpass.vista.EventoEntrada;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResumenVentasDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    private final EventoFachada eventos;

    public EventoController(EventoFachada eventos) {
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
        return eventos.listarEventos(desde, hasta, ciudad, idArtista, pagina, tamano);
    }

    @GetMapping("/{idEvento}")
    public EventoDetalleDto obtener(@PathVariable Integer idEvento) {
        return eventos.obtenerEvento(idEvento);
    }

    @GetMapping("/{idEvento}/ventas")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ResumenVentasDto> ventas(@PathVariable Integer idEvento) {
        return eventos.resumenVentas(idEvento);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EventoDto crear(@Valid @RequestBody EventoEntrada datos) {
        return eventos.crearEvento(datos);
    }

    @PutMapping("/{idEvento}")
    @PreAuthorize("hasRole('ADMIN')")
    public EventoDto actualizar(@PathVariable Integer idEvento, @Valid @RequestBody EventoEntrada datos) {
        return eventos.actualizarEvento(idEvento, datos);
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
