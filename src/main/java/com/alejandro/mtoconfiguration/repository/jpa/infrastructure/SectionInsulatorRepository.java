package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionInsulatorRepository extends CRUDRepository<SectionInsulator>, MessagingEntityGraphRepository<SectionInsulator> {

    List<SectionInsulator> findByStationId(Long stationId);
    List<SectionInsulator> findByStationNameContainingIgnoreCase(String stationName);



    /**
     * Pagina de identificadores para el republicado de datos maestros, por clave.
     *
     * <p>Solo ids y no entidades a proposito: el trabajo relee cada uno con
     * {@code findByIdForMessaging} para que el payload republicado sea identico al de un cambio
     * real. Traerse aqui la entidad cargaria un grafo distinto del de mensajeria, que habria que
     * descartar.</p>
     *
     * <p>Keyset sobre {@code id}: aqui no hace falta un orden funcional, solo cubrir cada fila una
     * vez, y el id ya es unico e indexado.</p>
     *
     * <p>El {@code @SQLRestriction} de {@code CRUDEntity} sigue aplicando, asi que los borrados
     * logicos quedan fuera solos: no se republica como UPDATED algo que esta borrado.</p>
     */
    @Query("select si.id from SectionInsulator si "
            + "where (:stationId is null or si.station.id = :stationId) and si.id > :lastId "
            + "order by si.id asc")
    List<Long> findIdsForRepublish(@Param("stationId") Long stationId,
                                   @Param("lastId") Long lastId,
                                   Pageable pageable);

    /**
     * Recuento de la misma seleccion que recorre {@code findIdsForRepublish}.
     *
     * <p>Se usa antes de crear la fila del trabajo, para rechazar con 400 una seleccion vacia o una
     * que supere {@code app.jobs.republish.max-items}, y para que el 202 lleve ya su
     * {@code totalItems} en lugar de descubrirlo al terminar.</p>
     */
    @Query("select count(si.id) from SectionInsulator si "
            + "where (:stationId is null or si.station.id = :stationId)")
    long countForRepublish(@Param("stationId") Long stationId);

    @Override
    @EntityGraph(attributePaths = {
            "station"
    })
    @Query("select si from SectionInsulator si where si.id = :id")
    Optional<SectionInsulator> findByIdForMessaging(@Param("id") Long id);
}
