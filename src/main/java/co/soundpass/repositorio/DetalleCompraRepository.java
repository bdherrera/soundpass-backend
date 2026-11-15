package co.soundpass.repositorio;

import co.soundpass.modelo.DetalleCompra;
import co.soundpass.modelo.DetalleCompraId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, DetalleCompraId> {

    List<DetalleCompra> findByOrdenIdIn(Collection<Integer> idsOrden);
}
