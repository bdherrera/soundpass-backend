package co.soundpass.servicio;

import co.soundpass.vista.ResumenVentasDto;
import java.util.List;

/** Reporte de ventas por evento. */
public interface VentasServicio {

    List<ResumenVentasDto> resumen(Integer idEvento);
}
