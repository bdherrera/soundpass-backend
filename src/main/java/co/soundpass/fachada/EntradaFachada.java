package co.soundpass.fachada;

import co.soundpass.modelo.EstadoEntrada;
import co.soundpass.servicio.CheckinServicio;
import co.soundpass.servicio.EntradaServicio;
import co.soundpass.vista.AsistenteDto;
import co.soundpass.vista.AsistenteEntrada;
import co.soundpass.vista.CheckinRespuesta;
import co.soundpass.vista.EntradaDto;
import co.soundpass.vista.Pagina;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Entradas del usuario, sus asistentes y el check-in en puerta. */
@Component
public class EntradaFachada {

    private final EntradaServicio entradas;
    private final CheckinServicio checkin;

    public EntradaFachada(EntradaServicio entradas, CheckinServicio checkin) {
        this.entradas = entradas;
        this.checkin = checkin;
    }

    public Pagina<EntradaDto> misEntradas(EstadoEntrada estado, int pagina, int tamano) {
        return entradas.mias(estado, pagina, tamano);
    }

    public AsistenteDto registrarAsistente(Integer idEntrada, AsistenteEntrada datos) {
        return entradas.registrarAsistente(idEntrada, datos);
    }

    public CheckinRespuesta hacerCheckin(UUID codigo) {
        return checkin.hacer(codigo);
    }
}
