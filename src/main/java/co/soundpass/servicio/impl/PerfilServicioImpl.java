package co.soundpass.servicio.impl;

import co.soundpass.servicio.PerfilServicio;
import co.soundpass.servicio.EventoServicio;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import co.soundpass.config.MongoEsquema;
import co.soundpass.error.ApiException;
import co.soundpass.modelo.DetalleEvento;
import co.soundpass.modelo.PerfilArtista;
import co.soundpass.repositorio.ArtistaRepository;
import co.soundpass.repositorio.EventoRepository;
import co.soundpass.vista.DetalleEventoDto;
import co.soundpass.vista.DetalleEventoEntrada;
import co.soundpass.vista.PerfilArtistaDto;
import co.soundpass.vista.PerfilArtistaEntrada;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Perfiles de artista y detalle de evento (MongoDB). Son opcionales: el catálogo no depende de ellos. */
@Service
public class PerfilServicioImpl implements PerfilServicio {

    private final MongoTemplate mongo;
    private final MongoEsquema esquema;
    private final ArtistaRepository artistas;
    private final EventoRepository eventos;
    private final EventoServicio eventoServicio;

    public PerfilServicioImpl(MongoTemplate mongo, MongoEsquema esquema, ArtistaRepository artistas,
                          EventoRepository eventos, EventoServicio eventoServicio) {
        this.mongo = mongo;
        this.esquema = esquema;
        this.artistas = artistas;
        this.eventos = eventos;
        this.eventoServicio = eventoServicio;
    }

    @Override
    public PerfilArtistaDto perfil(Integer idArtista) {
        artistaActivo(idArtista);
        PerfilArtista perfil = mongo.findOne(new Query(where("id_artista").is(idArtista)), PerfilArtista.class);
        if (perfil == null) {
            throw ApiException.noEncontrado("El artista no tiene perfil");
        }
        return PerfilArtistaDto.de(perfil);
    }

    /** Crea o reemplaza el perfil (un documento por artista). */
    @Override
    public PerfilArtistaDto guardarPerfil(Integer idArtista, PerfilArtistaEntrada datos) {
        artistaActivo(idArtista);
        esquema.asegurarListo();
        Update cambios = new Update()
                .set("biografia", datos.biografia())
                .set("redes", datos.redes())
                .set("enlaces", datos.enlaces())
                .set("galeria", datos.galeria())
                .set("version_esquema", MongoEsquema.VERSION);
        return PerfilArtistaDto.de(mongo.findAndModify(new Query(where("id_artista").is(idArtista)), cambios,
                FindAndModifyOptions.options().upsert(true).returnNew(true), PerfilArtista.class));
    }

    @Override
    public DetalleEventoDto detalle(Integer idEvento) {
        eventoServicio.cargarVisible(idEvento);
        DetalleEvento detalle = mongo.findOne(new Query(where("id_evento").is(idEvento)), DetalleEvento.class);
        if (detalle == null) {
            throw ApiException.noEncontrado("El evento no tiene detalle");
        }
        return DetalleEventoDto.de(detalle);
    }

    /** Crea o reemplaza el detalle (un documento por evento). */
    @Override
    public DetalleEventoDto guardarDetalle(Integer idEvento, DetalleEventoEntrada datos) {
        if (!eventos.existsById(idEvento)) {
            throw ApiException.noEncontrado("El evento no existe");
        }
        esquema.asegurarListo();
        Update cambios = new Update()
                .set("descripcion", datos.descripcion())
                .set("etiquetas", datos.etiquetas())
                .set("politicas", datos.politicas())
                .set("medios", datos.medios())
                .set("version_esquema", MongoEsquema.VERSION);
        return DetalleEventoDto.de(mongo.findAndModify(new Query(where("id_evento").is(idEvento)), cambios,
                FindAndModifyOptions.options().upsert(true).returnNew(true), DetalleEvento.class));
    }

    private void artistaActivo(Integer idArtista) {
        artistas.findByIdAndActivoTrue(idArtista).orElseThrow(() -> ApiException.noEncontrado("El artista no existe"));
    }
}
