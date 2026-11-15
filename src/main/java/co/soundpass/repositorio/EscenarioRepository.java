package co.soundpass.repositorio;

import co.soundpass.modelo.Escenario;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscenarioRepository extends JpaRepository<Escenario, Integer> {

    Optional<Escenario> findByIdAndActivoTrue(Integer id);

    /** {@code ciudad} llega en minúsculas (o null para no filtrar). */
    @Query("select s from Escenario s where s.activo = true "
            + "and (:ciudad is null or lower(s.ciudad) = :ciudad)")
    Page<Escenario> buscarActivos(@Param("ciudad") String ciudad, Pageable pageable);
}
