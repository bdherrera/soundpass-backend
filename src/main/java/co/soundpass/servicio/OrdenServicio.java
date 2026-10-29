package co.soundpass.servicio;

import co.soundpass.modelo.Orden;
import co.soundpass.vista.OrdenDto;
import co.soundpass.vista.Pagina;
import java.util.List;

/** Órdenes del usuario autenticado. */
public interface OrdenServicio {

    Pagina<OrdenDto> misOrdenes(int pagina, int tamano);

    OrdenDto obtener(Integer idOrden);

    /** Cancela una orden propia: sus entradas VENDIDA pasan a CANCELADA y la orden a REEMBOLSADA o CANCELADA. */
    OrdenDto cancelar(Integer idOrden);

    /** Arma los DTO con sus líneas y el total. Lo usan también otros servicios (la compra). */
    List<OrdenDto> armar(List<Orden> ordenes);
}
