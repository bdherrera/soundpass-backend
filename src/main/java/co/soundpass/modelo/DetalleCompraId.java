package co.soundpass.modelo;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record DetalleCompraId(Integer idOrden, Integer idTipoEntrada) implements Serializable {
}
