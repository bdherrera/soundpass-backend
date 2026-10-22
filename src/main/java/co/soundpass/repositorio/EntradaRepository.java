package co.soundpass.repositorio;

import co.soundpass.dominio.Entrada;
import co.soundpass.dominio.EstadoEntrada;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, Integer> {

    long countByTipoEntradaId(Integer idTipoEntrada);

    long countByTipoEntradaIdAndEstado(Integer idTipoEntrada, EstadoEntrada estado);

    /** Crea {@code n} entradas DISPONIBLE del tipo, en una sola sentencia. */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into entrada (id_tipo_entrada) select :tipo from generate_series(1, :n)", nativeQuery = true)
    int generar(@Param("tipo") Integer idTipoEntrada, @Param("n") int n);

    /** Quita hasta {@code n} entradas DISPONIBLE (las más nuevas) sin esperar a las que otra transacción tenga bloqueadas. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from entrada where id_entrada in (select id_entrada from entrada "
            + "where id_tipo_entrada = :tipo and estado = 'DISPONIBLE' order by id_entrada desc limit :n "
            + "for update skip locked)", nativeQuery = true)
    int borrarDisponibles(@Param("tipo") Integer idTipoEntrada, @Param("n") int n);

    /**
     * Toma hasta {@code n} entradas DISPONIBLE del tipo y las bloquea; las que otra compra ya tiene
     * bloqueadas se saltan en vez de esperar (FOR UPDATE SKIP LOCKED).
     */
    @Query(value = "select id_entrada from entrada where id_tipo_entrada = :tipo and estado = 'DISPONIBLE' "
            + "order by id_entrada limit :n for update skip locked", nativeQuery = true)
    List<Integer> bloquearDisponibles(@Param("tipo") Integer idTipoEntrada, @Param("n") int n);

    @Modifying(flushAutomatically = true)
    @Query(value = "update entrada set estado = 'VENDIDA', id_orden = :orden "
            + "where id_entrada in (:ids) and estado = 'DISPONIBLE'", nativeQuery = true)
    int marcarVendidas(@Param("orden") Integer idOrden, @Param("ids") Collection<Integer> ids);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.orden.id = :idOrden order by e.id")
    List<Entrada> deOrdenParaActualizar(@Param("idOrden") Integer idOrden);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.codigo = :codigo")
    Optional<Entrada> buscarPorCodigoParaActualizar(@Param("codigo") UUID codigo);
}