package co.soundpass.modelo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** Vista v_orden_total: el total de la orden se calcula, no se guarda. */
@Entity
@Immutable
@Table(name = "v_orden_total")
public class OrdenTotal {

    @Id
    @Column(name = "id_orden")
    private Integer idOrden;

    private Integer idUsuario;
    private String estado;
    private BigDecimal total;

    public Integer getIdOrden() { return idOrden; }
    public Integer getIdUsuario() { return idUsuario; }
    public String getEstado() { return estado; }
    public BigDecimal getTotal() { return total; }
}
