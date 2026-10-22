package co.soundpass.web.dto;

import co.soundpass.dominio.TipoEntrada;
import java.math.BigDecimal;

public record TipoEntradaDto(Integer idTipoEntrada, Integer idEvento, String nombre, BigDecimal precio, int cupo) {

    public static TipoEntradaDto de(TipoEntrada t) {
        return new TipoEntradaDto(t.getId(), t.getEvento().getId(), t.getNombre(), t.getPrecio(), t.getCupo());
    }
}