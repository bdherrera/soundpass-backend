package co.soundpass.mongo;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = MongoEsquema.DETALLE_EVENTO)
public class DetalleEvento {

    @Id
    private String id;
    @Field("id_evento")
    private Integer idEvento;
    private String descripcion;
    private List<String> etiquetas;
    private String politicas;
    private List<String> medios;
    @Field("version_esquema")
    private int versionEsquema;

    public String getId() { return id; }
    public Integer getIdEvento() { return idEvento; }
    public String getDescripcion() { return descripcion; }
    public List<String> getEtiquetas() { return etiquetas; }
    public String getPoliticas() { return politicas; }
    public List<String> getMedios() { return medios; }
    public int getVersionEsquema() { return versionEsquema; }
}