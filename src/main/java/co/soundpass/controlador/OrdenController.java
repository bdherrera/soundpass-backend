package co.soundpass.controlador;

import co.soundpass.fachada.CompraFachada;
import co.soundpass.vista.CompraSolicitud;
import co.soundpass.vista.OrdenDto;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResultadoCompra;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ordenes")
public class OrdenController {

    private final CompraFachada compras;

    public OrdenController(CompraFachada compras) {
        this.compras = compras;
    }

    /** 201 si se creó la orden; 200 si la clave de idempotencia ya existía. */
    @PostMapping
    public ResponseEntity<OrdenDto> comprar(@Valid @RequestBody CompraSolicitud solicitud) {
        ResultadoCompra r = compras.comprar(solicitud);
        return ResponseEntity.status(r.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(r.orden());
    }

    @GetMapping("/mias")
    public Pagina<OrdenDto> mias(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return compras.misOrdenes(pagina, tamano);
    }

    @GetMapping("/{idOrden}")
    public OrdenDto obtener(@PathVariable Integer idOrden) {
        return compras.obtenerOrden(idOrden);
    }

    @PostMapping("/{idOrden}/cancelacion")
    public OrdenDto cancelar(@PathVariable Integer idOrden) {
        return compras.cancelarOrden(idOrden);
    }
}
