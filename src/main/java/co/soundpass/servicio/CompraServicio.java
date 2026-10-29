package co.soundpass.servicio;

import co.soundpass.vista.CompraSolicitud;
import co.soundpass.vista.ResultadoCompra;
import java.util.List;

/** Compra de entradas. */
public interface CompraServicio {

    /** Compra en una sola transacción, idempotente por clave y sin sobreventa. */
    ResultadoCompra comprar(CompraSolicitud solicitud);

    /** ¿Ya existe una orden con esa clave de idempotencia? */
    boolean existeClave(String claveIdempotencia);

    /** Eventos de los tipos de entrada que pide la compra. */
    List<Integer> eventosDe(CompraSolicitud solicitud);
}
