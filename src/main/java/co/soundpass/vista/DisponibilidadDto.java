package co.soundpass.vista;

import java.math.BigDecimal;

public record DisponibilidadDto(Integer idTipoEntrada, String nombre, BigDecimal precio, Integer cupo,
                                Long disponibles) {
}
