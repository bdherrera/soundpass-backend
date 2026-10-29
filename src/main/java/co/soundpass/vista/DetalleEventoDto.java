package co.soundpass.vista;

import co.soundpass.modelo.DetalleEvento;
import java.util.List;

public record DetalleEventoDto(Integer idEvento, String descripcion, List<String> etiquetas, String politicas,
                               List<String> medios) {

    public static DetalleEventoDto de(DetalleEvento d) {
        return new DetalleEventoDto(d.getIdEvento(), d.getDescripcion(),
                d.getEtiquetas() == null ? List.of() : d.getEtiquetas(), d.getPoliticas(),
                d.getMedios() == null ? List.of() : d.getMedios());
    }
}
