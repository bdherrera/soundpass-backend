package co.soundpass.servicio;

import co.soundpass.dominio.Asistente;
import co.soundpass.dominio.Entrada;
import co.soundpass.dominio.EstadoEntrada;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.repositorio.AsistenteRepository;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.web.dto.AsistenteDto;
import co.soundpass.web.dto.AsistenteEntrada;
import co.soundpass.web.dto.EntradaDto;
import co.soundpass.web.dto.Pagina;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntradaServicio {

    private final EntradaRepository entradas;
    private final AsistenteRepository asistentes;

    public EntradaServicio(EntradaRepository entradas, AsistenteRepository asistentes) {
        this.entradas = entradas;
        this.asistentes = asistentes;
    }

    @Transactional(readOnly = true)
    public Pagina<EntradaDto> mias(EstadoEntrada estado, int pagina, int tamano) {
        Integer idUsuario = OrdenServicio.idUsuarioActual();
        var paginacion = PageRequest.of(pagina, tamano, Sort.by("id"));
        Page<Entrada> resultado = estado == null
                ? entradas.deUsuario(idUsuario, paginacion)
                : entradas.deUsuarioConEstado(idUsuario, estado, paginacion);
        Map<Integer, Asistente> asistentesPorEntrada = asistentes
                .findAllById(resultado.getContent().stream().map(Entrada::getId).toList()).stream()
                .collect(Collectors.toMap(Asistente::getId, Function.identity()));
        return Pagina.de(resultado.map(e -> EntradaDto.de(e,
                asistentesPorEntrada.containsKey(e.getId()) ? AsistenteDto.de(asistentesPorEntrada.get(e.getId())) : null)));
    }

    /** Solo el dueño de la entrada y solo mientras esté VENDIDA; un asistente por entrada. */
    @Transactional
    public AsistenteDto registrarAsistente(Integer idEntrada, AsistenteEntrada datos) {
        Entrada entrada = entradas.findByIdAndOrdenUsuarioId(idEntrada, OrdenServicio.idUsuarioActual())
                .orElseThrow(() -> ApiException.noEncontrado("La entrada no existe"));
        if (entrada.getEstado() != EstadoEntrada.VENDIDA) {
            throw ApiException.conflicto(CodigoError.TRANSICION_INVALIDA,
                    "Solo se puede registrar asistente en una entrada VENDIDA (está " + entrada.getEstado() + ")");
        }
        if (asistentes.existsById(idEntrada)) {
            throw ApiException.conflicto(CodigoError.DUPLICADO, "La entrada ya tiene un asistente");
        }
        Asistente asistente = new Asistente();
        asistente.setEntrada(entrada);
        asistente.setNombre(datos.nombre().trim());
        asistente.setDocumento(datos.documento().trim());
        asistente.setCorreo(datos.correo() == null || datos.correo().isBlank() ? null : datos.correo().trim());
        return AsistenteDto.de(asistentes.saveAndFlush(asistente));
    }
}