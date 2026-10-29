package co.soundpass.vista;

import co.soundpass.modelo.Artista;

public record ArtistaDto(Integer idArtista, String nombreArtistico, String pais, String genero) {

    public static ArtistaDto de(Artista a) {
        return new ArtistaDto(a.getId(), a.getNombreArtistico(), a.getPais(), a.getGenero());
    }
}
