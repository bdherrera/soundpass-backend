package co.soundpass.web;

import co.soundpass.servicio.CompraServicio;
import co.soundpass.servicio.OrdenServicio;
import co.soundpass.web.dto.CompraSolicitud;
import co.soundpass.web.dto.OrdenDto;
import co.soundpass.web.dto.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ordenes")
public class OrdenController {

    private final CompraServicio compras;
    private final OrdenServicio ordenes;

    public OrdenController(CompraServicio compras, OrdenServicio ordenes) {
        this.compras = compras;
        this.ordenes = ordenes;
    }

    /** 201 si se creó la orden; 200 si la clave de idempotencia ya existía. */
    @PostMapping
    public ResponseEntity<OrdenDto> comprar(@Valid @RequestBody CompraSolicitud solicitud) {
        CompraServicio.Resultado r = compras.comprar(solicitud);
        return ResponseEntity.status(r.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(r.orden());
    }

    @GetMapping("/mias")
    public Pagina<OrdenDto> mias(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return ordenes.misOrdenes(pagina, tamano);
    }

    @GetMapping("/{idOrden}")
    public OrdenDto obtener(@PathVariable Integer idOrden) {
        return ordenes.obtener(idOrden);
    }

    @PostMapping("/{idOrden}/cancelacion")
    public OrdenDto cancelar(@PathVariable Integer idOrden) {
        return ordenes.cancelar(idOrden);
    }
}