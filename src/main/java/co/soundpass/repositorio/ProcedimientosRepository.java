package co.soundpass.repositorio;

import co.soundpass.error.TraductorSql;
import co.soundpass.vista.ResumenVentasDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Llama a los procedimientos y funciones almacenados de PostgreSQL (db/init/02_logica_en_base_de_datos.sql).
 * Participa en la transacción del servicio que lo invoca; los errores de negocio de la base de datos
 * se convierten en ApiException.
 */
@Repository
public class ProcedimientosRepository {

    public record CompraRealizada(Integer idOrden, boolean creada) {
    }

    public record CheckinRealizado(Integer idEntrada, OffsetDateTime fecha) {
    }

    private final JdbcTemplate jdbc;

    public ProcedimientosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** sp_generar_entradas: crea {@code cantidad} entradas DISPONIBLE para el tipo. */
    public int generarEntradas(Integer idTipoEntrada, int cantidad) {
        return ejecutar(() -> jdbc.queryForObject("call sp_generar_entradas(?, ?, null)", Integer.class,
                idTipoEntrada, cantidad));
    }

    /** fn_comprar_entradas: compra idempotente en una transacción. {@code lineasJson}: [{idTipoEntrada, cantidad}]. */
    public CompraRealizada comprar(Integer idUsuario, String clave, String lineasJson) {
        return ejecutar(() -> jdbc.queryForObject(
                "select orden_id, creada from fn_comprar_entradas(?, ?, ?::jsonb)",
                (rs, fila) -> new CompraRealizada(rs.getInt(1), rs.getBoolean(2)),
                idUsuario, clave, lineasJson));
    }

    /** sp_cancelar_orden: devuelve el estado final de la orden (REEMBOLSADA o CANCELADA). */
    public String cancelarOrden(Integer idOrden, Integer idUsuario) {
        return ejecutar(() -> jdbc.queryForObject("call sp_cancelar_orden(?, ?, null)", String.class,
                idOrden, idUsuario));
    }

    /** fn_checkin: marca la entrada USADA y devuelve su id y la fecha del check-in. */
    public CheckinRealizado checkin(UUID codigo, Integer idUsuario) {
        return ejecutar(() -> jdbc.queryForObject("select entrada_id, fecha from fn_checkin(?, ?)",
                (rs, fila) -> new CheckinRealizado(rs.getInt(1), rs.getObject(2, OffsetDateTime.class)),
                codigo, idUsuario));
    }

    /** fn_resumen_ventas_evento: reporte por tipo de entrada. */
    public List<ResumenVentasDto> resumenVentas(Integer idEvento) {
        return ejecutar(() -> jdbc.query(
                "select tipo_id, tipo_nombre, tipo_cupo, disponibles, vendidas, usadas, canceladas, ingresos "
                        + "from fn_resumen_ventas_evento(?)",
                (rs, fila) -> new ResumenVentasDto(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getLong(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getBigDecimal(8)),
                idEvento));
    }

    private static <T> T ejecutar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (DataAccessException e) {
            throw TraductorSql.traducir(e);
        }
    }
}
