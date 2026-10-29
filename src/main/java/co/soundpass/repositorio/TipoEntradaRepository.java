package co.soundpass.repositorio;

import co.soundpass.modelo.TipoEntrada;
import co.soundpass.vista.DisponibilidadDto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoEntradaRepository extends JpaRepository<TipoEntrada, Integer> {

    List<TipoEntrada> findByEventoIdOrderById(Integer idEvento);

    /** Eventos a los que pertenecen los tipos de entrada dados. */
    @Query("select distinct t.evento.id from TipoEntrada t where t.id in :ids")
    List<Integer> eventosDe(@Param("ids") Collection<Integer> idsTipoEntrada);

    @Query("select new co.soundpass.vista.DisponibilidadDto(t.id, t.nombre, t.precio, t.cupo, count(e)) "
            + "from TipoEntrada t left join Entrada e on e.tipoEntrada = t "
            + "and e.estado = co.soundpass.modelo.EstadoEntrada.DISPONIBLE "
            + "where t.evento.id = :idEvento group by t.id, t.nombre, t.precio, t.cupo order by t.id")
    List<DisponibilidadDto> disponibilidad(@Param("idEvento") Integer idEvento);
}
