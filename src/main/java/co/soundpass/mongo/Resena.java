package co.soundpass.mongo;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Reseña de un evento; las referencias a PostgreSQL son solo ids (no hay claves foráneas entre motores). */
@Document(collection = MongoEsquema.RESENAS)
public class Resena {

    public static class UsuarioRef {
        private String nombre;

        public UsuarioRef() {
        }

        public UsuarioRef(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() { return nombre; }
    }

    @Id
    private String id;
    @Field("id_evento")
    private Integer idEvento;
    @Field("id_usuario")
    private Integer idUsuario;
    private UsuarioRef usuario;
    private int calificacion;
    private String comentario;
    private Instant fecha;
    @Field("version_esquema")
    private int versionEsquema = MongoEsquema.VERSION;

    public Resena() {
    }

    public Resena(Integer idEvento, Integer idUsuario, String nombreUsuario, int calificacion, String comentario) {
        this.idEvento = idEvento;
        this.idUsuario = idUsuario;
        this.usuario = new UsuarioRef(nombreUsuario);
        this.calificacion = calificacion;
        this.comentario = comentario;
        this.fecha = Instant.now();
    }

    public String getId() { return id; }
    public Integer getIdEvento() { return idEvento; }
    public Integer getIdUsuario() { return idUsuario; }
    public UsuarioRef getUsuario() { return usuario; }
    public int getCalificacion() { return calificacion; }
    public String getComentario() { return comentario; }
    public Instant getFecha() { return fecha; }
    public int getVersionEsquema() { return versionEsquema; }
}