package co.soundpass.web.dto;

import co.soundpass.dominio.Entrada;
import co.soundpass.dominio.EstadoEntrada;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntradaDto(Integer idEntrada, UUID codigo, EstadoEntrada estado, Integer idOrden,
                         Integer idTipoEntrada, String tipoEntrada, Integer idEvento, String evento,
                         AsistenteDto asistente) {

    public static EntradaDto de(Entrada e, AsistenteDto asistente) {
        var tipo = e.getTipoEntrada();
        return new EntradaDto(e.getId(), e.getCodigo(), e.getEstado(), e.getOrden().getId(), tipo.getId(),
                tipo.getNombre(), tipo.getEvento().getId(), tipo.getEvento().getNombre(), asistente);
    }
}