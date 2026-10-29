package co.soundpass.fachada;

import co.soundpass.servicio.ArtistaServicio;
import co.soundpass.servicio.EscenarioServicio;
import co.soundpass.vista.ArtistaDto;
import co.soundpass.vista.ArtistaEntrada;
import co.soundpass.vista.EscenarioDto;
import co.soundpass.vista.EscenarioEntrada;
import co.soundpass.vista.Pagina;
import org.springframework.stereotype.Component;

/** Catálogo de artistas y escenarios. */
@Component
public class CatalogoFachada {

    private final ArtistaServicio artistas;
    private final EscenarioServicio escenarios;

    public CatalogoFachada(ArtistaServicio artistas, EscenarioServicio escenarios) {
        this.artistas = artistas;
        this.escenarios = escenarios;
    }

    public Pagina<ArtistaDto> listarArtistas(String genero, String pais, int pagina, int tamano) {
        return artistas.listar(genero, pais, pagina, tamano);
    }

    public ArtistaDto obtenerArtista(Integer idArtista) {
        return artistas.obtener(idArtista);
    }

    public ArtistaDto crearArtista(ArtistaEntrada datos) {
        return artistas.crear(datos);
    }

    public ArtistaDto actualizarArtista(Integer idArtista, ArtistaEntrada datos) {
        return artistas.actualizar(idArtista, datos);
    }

    public void desactivarArtista(Integer idArtista) {
        artistas.desactivar(idArtista);
    }

    public Pagina<EscenarioDto> listarEscenarios(String ciudad, int pagina, int tamano) {
        return escenarios.listar(ciudad, pagina, tamano);
    }

    public EscenarioDto obtenerEscenario(Integer idEscenario) {
        return escenarios.obtener(idEscenario);
    }

    public EscenarioDto crearEscenario(EscenarioEntrada datos) {
        return escenarios.crear(datos);
    }

    public EscenarioDto actualizarEscenario(Integer idEscenario, EscenarioEntrada datos) {
        return escenarios.actualizar(idEscenario, datos);
    }

    public void desactivarEscenario(Integer idEscenario) {
        escenarios.desactivar(idEscenario);
    }
}
