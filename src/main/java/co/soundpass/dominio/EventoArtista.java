package co.soundpass.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "evento_artista")
public class EventoArtista {

    @EmbeddedId
    private EventoArtistaId id;

    @MapsId("idEvento")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evento")
    private Evento evento;

    @MapsId("idArtista")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_artista")
    private Artista artista;

    private int ordenPresentacion = 1;

    public EventoArtistaId getId() { return id; }
    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }
    public Artista getArtista() { return artista; }
    public void setArtista(Artista artista) { this.artista = artista; }
    public int getOrdenPresentacion() { return ordenPresentacion; }
    public void setOrdenPresentacion(int ordenPresentacion) { this.ordenPresentacion = ordenPresentacion; }
}