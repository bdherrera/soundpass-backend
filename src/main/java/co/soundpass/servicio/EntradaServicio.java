package co.soundpass.servicio;

import co.soundpass.modelo.EstadoEntrada;
import co.soundpass.vista.AsistenteDto;
import co.soundpass.vista.AsistenteEntrada;
import co.soundpass.vista.EntradaDto;
import co.soundpass.vista.Pagina;

/** Entradas del usuario autenticado y sus asistentes. */
public interface EntradaServicio {

    Pagina<EntradaDto> mias(EstadoEntrada estado, int pagina, int tamano);

    /** Solo el dueño de la entrada y solo mientras esté VENDIDA; un asistente por entrada. */
    AsistenteDto registrarAsistente(Integer idEntrada, AsistenteEntrada datos);
}
