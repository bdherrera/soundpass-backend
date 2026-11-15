package co.soundpass.vista;

import co.soundpass.modelo.PerfilArtista;
import java.util.List;
import java.util.Map;

public record PerfilArtistaDto(Integer idArtista, String biografia, Map<String, String> redes, List<String> enlaces,
                               List<String> galeria) {

    public static PerfilArtistaDto de(PerfilArtista p) {
        return new PerfilArtistaDto(p.getIdArtista(), p.getBiografia(),
                p.getRedes() == null ? Map.of() : p.getRedes(),
                p.getEnlaces() == null ? List.of() : p.getEnlaces(),
                p.getGaleria() == null ? List.of() : p.getGaleria());
    }
}
