package co.soundpass.vista;

import co.soundpass.modelo.TipoEntrada;
import java.math.BigDecimal;

public record TipoEntradaDto(Integer idTipoEntrada, Integer idEvento, String nombre, BigDecimal precio, int cupo) {

    public static TipoEntradaDto de(TipoEntrada t) {
        return new TipoEntradaDto(t.getId(), t.getEvento().getId(), t.getNombre(), t.getPrecio(), t.getCupo());
    }
}
