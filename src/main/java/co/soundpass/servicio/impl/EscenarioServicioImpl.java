package co.soundpass.servicio.impl;

import co.soundpass.servicio.EscenarioServicio;
import co.soundpass.error.ApiException;
import co.soundpass.modelo.Escenario;
import co.soundpass.repositorio.EscenarioRepository;
import co.soundpass.vista.EscenarioDto;
import co.soundpass.vista.EscenarioEntrada;
import co.soundpass.vista.Pagina;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscenarioServicioImpl implements EscenarioServicio {

    private final EscenarioRepository escenarios;

    public EscenarioServicioImpl(EscenarioRepository escenarios) {
        this.escenarios = escenarios;
    }

    @Transactional(readOnly = true)
    @Override
    public Pagina<EscenarioDto> listar(String ciudad, int pagina, int tamano) {
        String filtro = ciudad == null || ciudad.isBlank() ? null : ciudad.trim().toLowerCase(Locale.ROOT);
        var orden = Sort.by("nombre", "id");
        return Pagina.de(escenarios.buscarActivos(filtro, PageRequest.of(pagina, tamano, orden))
                .map(EscenarioDto::de));
    }

    @Transactional(readOnly = true)
    @Override
    public EscenarioDto obtener(Integer id) {
        return EscenarioDto.de(buscarActivo(id));
    }

    @Transactional
    @Override
    public EscenarioDto crear(EscenarioEntrada datos) {
        Escenario e = new Escenario();
        copiar(datos, e);
        return EscenarioDto.de(escenarios.save(e));
    }

    @Transactional
    @Override
    public EscenarioDto actualizar(Integer id, EscenarioEntrada datos) {
        Escenario e = buscarActivo(id);
        copiar(datos, e);
        return EscenarioDto.de(e);
    }

    /** Borrado lógico; es idempotente. */
    @Transactional
    @Override
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
