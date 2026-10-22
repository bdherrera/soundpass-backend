package co.soundpass.repositorio;

import co.soundpass.dominio.TipoEntrada;
import co.soundpass.web.dto.DisponibilidadDto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoEntradaRepository extends JpaRepository<TipoEntrada, Integer> {

    List<TipoEntrada> findByEventoIdOrderById(Integer idEvento);

    @Query("select new co.soundpass.web.dto.DisponibilidadDto(t.id, t.nombre, t.precio, t.cupo, count(e)) "
            + "from TipoEntrada t left join Entrada e on e.tipoEntrada = t "
            + "and e.estado = co.soundpass.dominio.EstadoEntrada.DISPONIBLE "
            + "where t.evento.id = :idEvento group by t.id, t.nombre, t.precio, t.cupo order by t.id")
    List<DisponibilidadDto> disponibilidad(@Param("idEvento") Integer idEvento);
}