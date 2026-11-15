package co.soundpass.modelo;

import co.soundpass.config.MongoEsquema;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = MongoEsquema.PERFILES_ARTISTA)
public class PerfilArtista {

    @Id
    private String id;
    @Field("id_artista")
    private Integer idArtista;
    private String biografia;
    private Map<String, String> redes;
    private List<String> enlaces;
    private List<String> galeria;
    @Field("version_esquema")
    private int versionEsquema;

    public String getId() { return id; }
    public Integer getIdArtista() { return idArtista; }
    public String getBiografia() { return biografia; }
    public Map<String, String> getRedes() { return redes; }
    public List<String> getEnlaces() { return enlaces; }
    public List<String> getGaleria() { return galeria; }
    public int getVersionEsquema() { return versionEsquema; }
}
