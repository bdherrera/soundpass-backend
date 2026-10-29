package co.soundpass.modelo;

import jakarta.persistence.*;

@Entity
@Table(name = "artista")
public class Artista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_artista")
    private Integer id;

    private String nombreArtistico;
    private String pais;
    private String genero;
    private boolean activo = true;

    public Integer getId() { return id; }
    public String getNombreArtistico() { return nombreArtistico; }
    public void setNombreArtistico(String nombreArtistico) { this.nombreArtistico = nombreArtistico; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
