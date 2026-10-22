package co.soundpass.repositorio;

import co.soundpass.dominio.Orden;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenRepository extends JpaRepository<Orden, Integer> {

    Optional<Orden> findByClaveIdempotencia(String claveIdempotencia);

    Optional<Orden> findByIdAndUsuarioId(Integer id, Integer idUsuario);

    Page<Orden> findByUsuarioId(Integer idUsuario, Pageable pageable);

    /** Bloquea la fila de la orden hasta el fin de la transacción (evita cancelaciones simultáneas). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Orden o where o.id = :id and o.usuario.id = :idUsuario")
    Optional<Orden> buscarPropiaParaActualizar(@Param("id") Integer id, @Param("idUsuario") Integer idUsuario);

    /**
     * Reserva la clave de idempotencia creando la orden. Devuelve su id, o null si la clave ya existía
     * (si otra transacción la está creando, espera a que termine). No aborta la transacción.
     */
    @Query(value = "insert into orden (id_usuario, clave_idempotencia, estado) "
            + "values (:usuario, :clave, 'PAGADA') on conflict (clave_idempotencia) do nothing returning id_orden",
            nativeQuery = true)
    Integer crearSiNoExiste(@Param("usuario") Integer idUsuario, @Param("clave") String clave);
}