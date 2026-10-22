package co.soundpass.web.dto;

import java.math.BigDecimal;

public record DisponibilidadDto(Integer idTipoEntrada, String nombre, BigDecimal precio, Integer cupo,
                                Long disponibles) {
}