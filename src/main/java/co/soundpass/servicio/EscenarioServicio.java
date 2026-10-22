package co.soundpass.servicio;

import co.soundpass.dominio.Escenario;
import co.soundpass.error.ApiException;
import co.soundpass.repositorio.EscenarioRepository;
import co.soundpass.web.dto.EscenarioDto;
import co.soundpass.web.dto.EscenarioEntrada;
import co.soundpass.web.dto.Pagina;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscenarioServicio {

    private final EscenarioRepository escenarios;

    public EscenarioServicio(EscenarioRepository escenarios) {
        this.escenarios = escenarios;
    }

    @Transactional(readOnly = true)
    public Pagina<EscenarioDto> listar(String ciudad, int pagina, int tamano) {
        String filtro = ciudad == null || ciudad.isBlank() ? null : ciudad.trim().toLowerCase(Locale.ROOT);
        var orden = Sort.by("nombre", "id");
        return Pagina.de(escenarios.buscarActivos(filtro, PageRequest.of(pagina, tamano, orden))
                .map(EscenarioDto::de));
    }

    @Transactional(readOnly = true)
    public EscenarioDto obtener(Integer id) {
        return EscenarioDto.de(buscarActivo(id));
    }

    @Transactional
    public EscenarioDto crear(EscenarioEntrada datos) {
        Escenario e = new Escenario();
        copiar(datos, e);
        return EscenarioDto.de(escenarios.save(e));
    }

    @Transactional
    public EscenarioDto actualizar(Integer id, EscenarioEntrada datos) {
        Escenario e = buscarActivo(id);
        copiar(datos, e);
        return EscenarioDto.de(e);
    }

    /** Borrado lógico; es idempotente. */
    @Transactional
    public void desactivar(Integer id) {
        escenarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("El escenario no existe")).setActivo(false);
    }

    private Escenario buscarActivo(Integer id) {
        return escenarios.findByIdAndActivoTrue(id)
                .orElseThrow(() -> ApiException.noEncontrado("El escenario no existe"));
    }

    private static void copiar(EscenarioEntrada datos, Escenario e) {
        e.setNombre(datos.nombre().trim());
        e.setCiudad(datos.ciudad().trim());
        e.setDireccion(datos.direccion());
        e.setCapacidad(datos.capacidad());
    }
}