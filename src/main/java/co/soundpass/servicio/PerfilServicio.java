package co.soundpass.servicio;

import co.soundpass.vista.DetalleEventoDto;
import co.soundpass.vista.DetalleEventoEntrada;
import co.soundpass.vista.PerfilArtistaDto;
import co.soundpass.vista.PerfilArtistaEntrada;

/** Perfiles de artista y detalle de evento (MongoDB). Son opcionales: el catálogo no depende de ellos. */
public interface PerfilServicio {

    PerfilArtistaDto perfil(Integer idArtista);

    /** Crea o reemplaza el perfil (un documento por artista). */
    PerfilArtistaDto guardarPerfil(Integer idArtista, PerfilArtistaEntrada datos);

    DetalleEventoDto detalle(Integer idEvento);

    /** Crea o reemplaza el detalle (un documento por evento). */
    DetalleEventoDto guardarDetalle(Integer idEvento, DetalleEventoEntrada datos);
}
