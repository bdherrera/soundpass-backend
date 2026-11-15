package co.soundpass.repositorio;

import co.soundpass.modelo.Artista;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtistaRepository extends JpaRepository<Artista, Integer> {

    Optional<Artista> findByIdAndActivoTrue(Integer id);

    @Query("select a from Artista a where a.activo = true "
            + "and (:genero is null or a.genero = :genero) and (:pais is null or a.pais = :pais)")
    Page<Artista> buscarActivos(@Param("genero") String genero, @Param("pais") String pais, Pageable pageable);
}
