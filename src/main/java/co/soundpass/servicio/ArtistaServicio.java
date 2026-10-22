package co.soundpass.servicio;

import co.soundpass.dominio.Artista;
import co.soundpass.error.ApiException;
import co.soundpass.repositorio.ArtistaRepository;
import co.soundpass.web.dto.ArtistaDto;
import co.soundpass.web.dto.ArtistaEntrada;
import co.soundpass.web.dto.Pagina;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistaServicio {

    private final ArtistaRepository artistas;

    public ArtistaServicio(ArtistaRepository artistas) {
        this.artistas = artistas;
    }

    @Transactional(readOnly = true)
    public Pagina<ArtistaDto> listar(String genero, String pais, int pagina, int tamano) {
        var orden = Sort.by("nombreArtistico", "id");
        return Pagina.de(artistas.buscarActivos(genero, pais, PageRequest.of(pagina, tamano, orden))
                .map(ArtistaDto::de));
    }

    @Transactional(readOnly = true)
    public ArtistaDto obtener(Integer id) {
        return ArtistaDto.de(buscarActivo(id));
    }

    @Transactional
    public ArtistaDto crear(ArtistaEntrada datos) {
        Artista a = new Artista();
        copiar(datos, a);
        return ArtistaDto.de(artistas.save(a));
    }

    @Transactional
    public ArtistaDto actualizar(Integer id, ArtistaEntrada datos) {
        Artista a = buscarActivo(id);
        copiar(datos, a);
        return ArtistaDto.de(a);
    }

    /** Borrado lógico; es idempotente. */
    @Transactional
    public void desactivar(Integer id) {
        artistas.findById(id).orElseThrow(() -> ApiException.noEncontrado("El artista no existe")).setActivo(false);
    }

    private Artista buscarActivo(Integer id) {
        return artistas.findByIdAndActivoTrue(id).orElseThrow(() -> ApiException.noEncontrado("El artista no existe"));
    }

    private static void copiar(ArtistaEntrada datos, Artista a) {
        a.setNombreArtistico(datos.nombreArtistico().trim());
        a.setPais(datos.pais());
        a.setGenero(datos.genero());
    }
}