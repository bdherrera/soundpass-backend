package co.soundpass.vista;

import java.util.List;
import org.springframework.data.domain.Page;

public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <T> Pagina<T> de(Page<T> p) {
        return new Pagina<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
