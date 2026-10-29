package co.soundpass.servicio.impl;

import co.soundpass.servicio.ResenaServicio;
import co.soundpass.servicio.EventoServicio;
import co.soundpass.seguridad.Sesion;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import co.soundpass.config.MongoEsquema;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.modelo.EstadoEntrada;
import co.soundpass.modelo.Resena;
import co.soundpass.repositorio.EntradaRepository;
import co.soundpass.repositorio.EventoRepository;
import co.soundpass.repositorio.UsuarioRepository;
import co.soundpass.vista.Pagina;
import co.soundpass.vista.ResenaDto;
import co.soundpass.vista.ResenaEntrada;
import co.soundpass.vista.ResumenResenasDto;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Reseñas en MongoDB. No es transaccional a propósito: la regla 6 se valida primero en PostgreSQL
 * y solo después se escribe en MongoDB, sin mantener una transacción relacional abierta.
 */
@Service
public class ResenaServicioImpl implements ResenaServicio {

    private final MongoTemplate mongo;
    private final MongoEsquema esquema;
    private final EventoRepository eventos;
    private final EntradaRepository entradas;
    private final UsuarioRepository usuarios;
    private final EventoServicio eventoServicio;

    public ResenaServicioImpl(MongoTemplate mongo, MongoEsquema esquema, EventoRepository eventos,
                          EntradaRepository entradas, UsuarioRepository usuarios, EventoServicio eventoServicio) {
        this.mongo = mongo;
        this.esquema = esquema;
        this.eventos = eventos;
        this.entradas = entradas;
        this.usuarios = usuarios;
        this.eventoServicio = eventoServicio;
    }

    @Override
    public ResenaDto crear(Integer idEvento, ResenaEntrada datos) {
        Integer idUsuario = Sesion.idUsuarioActual();
        if (!eventos.existsById(idEvento)) {
            throw ApiException.noEncontrado("El evento no existe");
        }
        if (!entradas.existsByEstadoAndOrdenUsuarioIdAndTipoEntradaEventoId(EstadoEntrada.USADA, idUsuario, idEvento)) {
            throw ApiException.prohibido("Solo puedes reseñar eventos a los que asististe (entrada USADA)");
        }
        String nombre = usuarios.findById(idUsuario).orElseThrow().getNombre();
        String comentario = datos.comentario() == null || datos.comentario().isBlank() ? null : datos.comentario().trim();

        esquema.asegurarListo();
        if (mongo.exists(query(idEvento, idUsuario), Resena.class)) {
            throw yaReseno();
        }
        try {
            return ResenaDto.de(mongo.insert(new Resena(idEvento, idUsuario, nombre, datos.calificacion(), comentario)));
        } catch (DuplicateKeyException e) {
            throw yaReseno(); // dos solicitudes simultáneas: la gana el índice único
        }
    }

    @Override
    public Pagina<ResenaDto> listar(Integer idEvento, int pagina, int tamano) {
        eventoServicio.cargarVisible(idEvento);
        Query consulta = new Query(where("id_evento").is(idEvento));
        long total = mongo.count(consulta, Resena.class);
        var orden = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("_id"));
        var resenas = mongo.find(consulta.with(PageRequest.of(pagina, tamano, orden)), Resena.class).stream()
                .map(ResenaDto::de).toList();
        int totalPaginas = (int) Math.ceil(total / (double) tamano);
        return new Pagina<>(resenas, pagina, tamano, total, totalPaginas);
    }

    @Override
    public ResumenResenasDto resumen(Integer idEvento) {
        eventoServicio.cargarVisible(idEvento);
        var agregacion = Aggregation.newAggregation(
                Aggregation.match(where("id_evento").is(idEvento)),
                Aggregation.group().avg("calificacion").as("promedio").count().as("total"));
        Document r = mongo.aggregate(agregacion, MongoEsquema.RESENAS, Document.class).getUniqueMappedResult();
        if (r == null) {
            return new ResumenResenasDto(idEvento, 0, 0);
        }
        double promedio = Math.round(r.get("promedio", Number.class).doubleValue() * 100) / 100.0;
        return new ResumenResenasDto(idEvento, promedio, r.get("total", Number.class).longValue());
    }

    private static Query query(Integer idEvento, Integer idUsuario) {
        return new Query(where("id_evento").is(idEvento).and("id_usuario").is(idUsuario));
    }

    private static ApiException yaReseno() {
        return ApiException.conflicto(CodigoError.DUPLICADO, "Ya reseñaste este evento");
    }
}
