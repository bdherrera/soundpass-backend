package co.soundpass.servicio.impl;

import co.soundpass.servicio.VentasServicio;
import co.soundpass.repositorio.ProcedimientosRepository;
import co.soundpass.vista.ResumenVentasDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reporte de ventas por evento (función fn_resumen_ventas_evento). */
@Service
public class VentasServicioImpl implements VentasServicio {

    private final ProcedimientosRepository procedimientos;

    public VentasServicioImpl(ProcedimientosRepository procedimientos) {
        this.procedimientos = procedimientos;
    }

    @Transactional(readOnly = true)
    @Override
    public List<ResumenVentasDto> resumen(Integer idEvento) {
        return procedimientos.resumenVentas(idEvento);
    }
}
