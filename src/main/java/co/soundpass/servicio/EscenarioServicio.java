package co.soundpass.servicio;

import co.soundpass.vista.EscenarioDto;
import co.soundpass.vista.EscenarioEntrada;
import co.soundpass.vista.Pagina;

/** Catálogo de escenarios. */
public interface EscenarioServicio {

    Pagina<EscenarioDto> listar(String ciudad, int pagina, int tamano);

    EscenarioDto obtener(Integer id);

    EscenarioDto crear(EscenarioEntrada datos);

    EscenarioDto actualizar(Integer id, EscenarioEntrada datos);

    /** Borrado lógico; es idempotente. */
    void desactivar(Integer id);
}
