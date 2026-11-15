package co.soundpass.servicio;

import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResenaDto;
import co.soundpass.vista.ResenaEntrada;
import co.soundpass.vista.ResumenResenasDto;

/** Reseñas de eventos (MongoDB). */
public interface ResenaServicio {

    /** Solo con una entrada USADA del evento, validado en PostgreSQL antes de escribir en MongoDB. */
    ResenaDto crear(Integer idEvento, ResenaEntrada datos);

    Pagina<ResenaDto> listar(Integer idEvento, int pagina, int tamano);

    ResumenResenasDto resumen(Integer idEvento);
}
