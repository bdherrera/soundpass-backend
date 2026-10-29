package co.soundpass.fachada;

import co.soundpass.error.ApiException;
import co.soundpass.seguridad.Sesion;
import co.soundpass.servicio.ColaServicio;
import co.soundpass.servicio.CompraServicio;
import co.soundpass.servicio.OrdenServicio;
import co.soundpass.vista.CompraSolicitud;
import co.soundpass.vista.OrdenDto;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResultadoCompra;
import java.util.List;
import org.springframework.stereotype.Component;

/** Compra de entradas y gestión de las órdenes del usuario autenticado. */
@Component
public class CompraFachada {

    private final CompraServicio compras;
    private final OrdenServicio ordenes;
    private final ColaServicio cola;

    public CompraFachada(CompraServicio compras, OrdenServicio ordenes, ColaServicio cola) {
        this.compras = compras;
        this.ordenes = ordenes;
        this.cola = cola;
    }

    /**
     * Orquesta tres pasos en servicios distintos: (1) verifica el turno en la cola de reserva de los eventos que
     * la tengan (MongoDB, opcional), (2) compra en PostgreSQL en una transacción y (3) cierra el turno. Repetir una
     * clave de idempotencia devuelve la orden original sin volver a pasar por la cola.
     */
    public ResultadoCompra comprar(CompraSolicitud solicitud) {
        Integer idUsuario = Sesion.usuarioActual()
                .orElseThrow(() -> ApiException.noAutenticado("Se requiere autenticación")).id();
        List<Integer> eventos = List.of();
        if (!compras.existeClave(solicitud.claveIdempotencia())) {
            eventos = compras.eventosDe(solicitud);
            cola.verificarAcceso(idUsuario, eventos);
        }
        ResultadoCompra resultado = compras.comprar(solicitud);
        if (resultado.creada()) {
            cola.completar(idUsuario, eventos);
        }
        return resultado;
    }

    public Pagina<OrdenDto> misOrdenes(int pagina, int tamano) {
        return ordenes.misOrdenes(pagina, tamano);
    }

    public OrdenDto obtenerOrden(Integer idOrden) {
        return ordenes.obtener(idOrden);
    }

    public OrdenDto cancelarOrden(Integer idOrden) {
        return ordenes.cancelar(idOrden);
    }
}
