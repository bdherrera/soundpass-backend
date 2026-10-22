package co.soundpass.repositorio;

import co.soundpass.dominio.EventoArtista;
import co.soundpass.dominio.EventoArtistaId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoArtistaRepository extends JpaRepository<EventoArtista, EventoArtistaId> {

    @Query("select ea from EventoArtista ea join fetch ea.artista a where ea.id.idEvento = :idEvento "
            + "order by ea.ordenPresentacion, a.nombreArtistico")
    List<EventoArtista> deEvento(@Param("idEvento") Integer idEvento);
}