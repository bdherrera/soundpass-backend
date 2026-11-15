package co.soundpass.repositorio;

import co.soundpass.modelo.Orden;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenRepository extends JpaRepository<Orden, Integer> {

    Optional<Orden> findByClaveIdempotencia(String claveIdempotencia);

    Optional<Orden> findByIdAndUsuarioId(Integer id, Integer idUsuario);

    Page<Orden> findByUsuarioId(Integer idUsuario, Pageable pageable);
}
