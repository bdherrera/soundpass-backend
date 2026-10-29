package co.soundpass.fachada;

import co.soundpass.servicio.PerfilServicio;
import co.soundpass.servicio.ResenaServicio;
import co.soundpass.vista.DetalleEventoDto;
import co.soundpass.vista.DetalleEventoEntrada;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.PerfilArtistaDto;
import co.soundpass.vista.PerfilArtistaEntrada;
import co.soundpass.vista.ResenaDto;
import co.soundpass.vista.ResenaEntrada;
import co.soundpass.vista.ResumenResenasDto;
import org.springframework.stereotype.Component;

/** Contenido no estructurado que vive en MongoDB: reseñas, perfiles de artista y detalle de evento. */
@Component
public class ContenidoFachada {

    private final ResenaServicio resenas;
    private final PerfilServicio perfiles;

    public ContenidoFachada(ResenaServicio resenas, PerfilServicio perfiles) {
        this.resenas = resenas;
        this.perfiles = perfiles;
    }

    public Pagina<ResenaDto> listarResenas(Integer idEvento, int pagina, int tamano) {
        return resenas.listar(idEvento, pagina, tamano);
    }

    public ResenaDto crearResena(Integer idEvento, ResenaEntrada datos) {
        return resenas.crear(idEvento, datos);
    }

    public ResumenResenasDto resumenResenas(Integer idEvento) {
        return resenas.resumen(idEvento);
    }

    public PerfilArtistaDto obtenerPerfilArtista(Integer idArtista) {
        return perfiles.perfil(idArtista);
    }

    public PerfilArtistaDto guardarPerfilArtista(Integer idArtista, PerfilArtistaEntrada datos) {
        return perfiles.guardarPerfil(idArtista, datos);
    }

    public DetalleEventoDto obtenerDetalleEvento(Integer idEvento) {
        return perfiles.detalle(idEvento);
    }

    public DetalleEventoDto guardarDetalleEvento(Integer idEvento, DetalleEventoEntrada datos) {
        return perfiles.guardarDetalle(idEvento, datos);
    }
}
