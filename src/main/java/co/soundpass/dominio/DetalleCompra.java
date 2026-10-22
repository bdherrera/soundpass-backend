package co.soundpass.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "detalle_compra")
public class DetalleCompra {

    @EmbeddedId
    private DetalleCompraId id;

    @MapsId("idOrden")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden")
    private Orden orden;

    @MapsId("idTipoEntrada")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_entrada")
    private TipoEntrada tipoEntrada;

    private int cantidad;
    private BigDecimal precioUnitario;

    /** Columna generada por la base de datos (cantidad * precio_unitario): solo lectura. */
    @Column(insertable = false, updatable = false)
    private BigDecimal subtotal;

    public DetalleCompraId getId() { return id; }
    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }
    public TipoEntrada getTipoEntrada() { return tipoEntrada; }
    public void setTipoEntrada(TipoEntrada tipoEntrada) { this.tipoEntrada = tipoEntrada; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
}