package co.soundpass.servicio.impl;

import co.soundpass.servicio.ArtistaServicio;
import co.soundpass.error.ApiException;
import co.soundpass.modelo.Artista;
import co.soundpass.repositorio.ArtistaRepository;
import co.soundpass.vista.ArtistaDto;
import co.soundpass.vista.ArtistaEntrada;
import co.soundpass.vista.Pagina;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistaServicioImpl implements ArtistaServicio {

    private final ArtistaRepository artistas;

    public ArtistaServicioImpl(ArtistaRepository artistas) {
        this.artistas = artistas;
    }

    @Transactional(readOnly = true)
    @Override
    public Pagina<ArtistaDto> listar(String genero, String pais, int pagina, int tamano) {
        var orden = Sort.by("nombreArtistico", "id");
        return Pagina.de(artistas.buscarActivos(genero, pais, PageRequest.of(pagina, tamano, orden))
                .map(ArtistaDto::de));
    }

    @Transactional(readOnly = true)
    @Override
    public ArtistaDto obtener(Integer id) {
        return ArtistaDto.de(buscarActivo(id));
    }

    @Transactional
    @Override
    public ArtistaDto crear(ArtistaEntrada datos) {
        Artista a = new Artista();
        copiar(datos, a);
        return ArtistaDto.de(artistas.save(a));
    }

    @Transactional
    @Override
    public ArtistaDto actualizar(Integer id, ArtistaEntrada datos) {
        Artista a = buscarActivo(id);
        copiar(datos, a);
        return ArtistaDto.de(a);
    }

    /** Borrado lógico; es idempotente. */
    @Transactional
    @Override
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
