package co.soundpass.web.dto;

import co.soundpass.dominio.Artista;

public record ArtistaDto(Integer idArtista, String nombreArtistico, String pais, String genero) {

    public static ArtistaDto de(Artista a) {
        return new ArtistaDto(a.getId(), a.getNombreArtistico(), a.getPais(), a.getGenero());
    }
}