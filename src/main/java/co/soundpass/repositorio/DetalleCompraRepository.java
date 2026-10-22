package co.soundpass.repositorio;

import co.soundpass.dominio.DetalleCompra;
import co.soundpass.dominio.DetalleCompraId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, DetalleCompraId> {

    List<DetalleCompra> findByOrdenIdIn(Collection<Integer> idsOrden);
}