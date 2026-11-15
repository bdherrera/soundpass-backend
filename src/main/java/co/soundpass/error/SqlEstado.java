package co.soundpass.error;

import java.sql.SQLException;

/** Lee el SQLSTATE de PostgreSQL de la cadena de causas de una excepción. */
public final class SqlEstado {

    public static final String EXCLUSION = "23P01";
    public static final String UNICA = "23505";

    private SqlEstado() {
    }

    public static String de(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
