package co.soundpass.servicio;

import co.soundpass.vista.ArtistaDto;
import co.soundpass.vista.ArtistaEntrada;
import co.soundpass.vista.Pagina;

/** Catálogo de artistas. */
public interface ArtistaServicio {

    Pagina<ArtistaDto> listar(String genero, String pais, int pagina, int tamano);

    ArtistaDto obtener(Integer id);

    ArtistaDto crear(ArtistaEntrada datos);

    ArtistaDto actualizar(Integer id, ArtistaEntrada datos);

    /** Borrado lógico; es idempotente. */
    void desactivar(Integer id);
}
