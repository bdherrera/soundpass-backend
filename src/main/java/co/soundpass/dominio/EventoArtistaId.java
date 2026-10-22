package co.soundpass.dominio;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record EventoArtistaId(Integer idEvento, Integer idArtista) implements Serializable {
}