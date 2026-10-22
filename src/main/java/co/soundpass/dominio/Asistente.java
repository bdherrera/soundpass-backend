package co.soundpass.dominio;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** El identificador es el de su entrada (clave primaria compartida). */
@Entity
@Table(name = "asistente")
public class Asistente {

    @Id
    @Column(name = "id_entrada")
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entrada")
    private Entrada entrada;

    private String nombre;
    private String documento;
    private String correo;
    private OffsetDateTime fechaCheckin;

    public Integer getId() { return id; }
    public Entrada getEntrada() { return entrada; }
    public void setEntrada(Entrada entrada) { this.entrada = entrada; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public OffsetDateTime getFechaCheckin() { return fechaCheckin; }
    public void setFechaCheckin(OffsetDateTime fechaCheckin) { this.fechaCheckin = fechaCheckin; }
}