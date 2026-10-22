package co.soundpass.servicio;

import co.soundpass.dominio.Asistente;
import co.soundpass.dominio.Entrada;
import co.soundpass.dominio.EstadoEntrada;
import co.soundpass.dominio.EstadoEvento;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.repositorio.AsistenteRepository;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.web.dto.CheckinRespuesta;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinServicio {

    private final EntradaRepository entradas;
    private final AsistenteRepository asistentes;

    public CheckinServicio(EntradaRepository entradas, AsistenteRepository asistentes) {
        this.entradas = entradas;
        this.asistentes = asistentes;
    }

    /**
     * Bloquea la entrada, exige que esté VENDIDA con asistente registrado y evento PUBLICADO, y la pasa a USADA.
     * Dos check-in simultáneos del mismo código: el segundo espera el bloqueo y ve la entrada ya USADA.
     */
    @Transactional
    public CheckinRespuesta hacer(UUID codigo) {
        Entrada entrada = entradas.buscarPorCodigoParaActualizar(codigo)
                .orElseThrow(() -> ApiException.noEncontrado("No existe una entrada con ese código"));
        if (entrada.getEstado() != EstadoEntrada.VENDIDA) {
            throw rechazo("La entrada no admite check-in: está " + entrada.getEstado());
        }
        if (entrada.getTipoEntrada().getEvento().getEstado() != EstadoEvento.PUBLICADO) {
            throw rechazo("El evento no está publicado");
        }
        Asistente asistente = asistentes.findById(entrada.getId())
                .orElseThrow(() -> rechazo("La entrada no tiene asistente registrado"));
        OffsetDateTime ahora = OffsetDateTime.now();
        asistente.setFechaCheckin(ahora);
        entrada.setEstado(EstadoEntrada.USADA);
        asistentes.flush();
        return new CheckinRespuesta(entrada.getId(), entrada.getEstado(), ahora);
    }

    private static ApiException rechazo(String mensaje) {
        return ApiException.conflicto(CodigoError.TRANSICION_INVALIDA, mensaje);
    }
}