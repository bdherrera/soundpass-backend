package co.soundpass.repositorio;

import co.soundpass.modelo.Entrada;
import co.soundpass.modelo.EstadoEntrada;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Lecturas de entradas. Crear, vender, cancelar y usar entradas lo hacen los procedimientos almacenados. */
public interface EntradaRepository extends JpaRepository<Entrada, Integer> {

    long countByTipoEntradaId(Integer idTipoEntrada);

    long countByTipoEntradaIdAndEstado(Integer idTipoEntrada, EstadoEntrada estado);

    /** Quita hasta {@code n} entradas DISPONIBLE (las más nuevas) sin esperar a las que otra transacción tenga bloqueadas. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from entrada where id_entrada in (select id_entrada from entrada "
            + "where id_tipo_entrada = :tipo and estado = 'DISPONIBLE' order by id_entrada desc limit :n "
            + "for update skip locked)", nativeQuery = true)
    int borrarDisponibles(@Param("tipo") Integer idTipoEntrada, @Param("n") int n);

    @Query(value = "select e from Entrada e join fetch e.tipoEntrada t join fetch t.evento "
            + "where e.orden.usuario.id = :usuario",
            countQuery = "select count(e) from Entrada e where e.orden.usuario.id = :usuario")
    Page<Entrada> deUsuario(@Param("usuario") Integer idUsuario, Pageable pageable);

    @Query(value = "select e from Entrada e join fetch e.tipoEntrada t join fetch t.evento "
            + "where e.orden.usuario.id = :usuario and e.estado = :estado",
            countQuery = "select count(e) from Entrada e where e.orden.usuario.id = :usuario and e.estado = :estado")
    Page<Entrada> deUsuarioConEstado(@Param("usuario") Integer idUsuario, @Param("estado") EstadoEntrada estado,
                                     Pageable pageable);

    Optional<Entrada> findByIdAndOrdenUsuarioId(Integer id, Integer idUsuario);

    /** ¿El usuario tiene una entrada en ese estado de algún tipo del evento? (regla 6 de las reseñas) */
    boolean existsByEstadoAndOrdenUsuarioIdAndTipoEntradaEventoId(EstadoEntrada estado, Integer idUsuario, Integer idEvento);
}
