package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackAccessPointRepository extends CRUDRepository<TrackAccessPoint>,
        MessagingEntityGraphRepository<TrackAccessPoint>, JpaSpecificationExecutor<TrackAccessPoint> {

    /** Los accesos de una vía, por KP (los que no lo tienen, al final) y por id para desempatar. */
    List<TrackAccessPoint> findByTrackIdOrderByKpAscIdAsc(Long trackId);

    /**
     * Pagina de identificadores para el republicado, por clave, como
     * {@code SectionInsulatorRepository.findIdsForRepublish}: solo ids, y cada uno se relee despues con
     * {@link #findByIdForMessaging}. Acota por via, que es como lo pide el consumidor; lo borrado queda
     * fuera por el {@code @SQLRestriction} de {@code CRUDEntity}.
     */
    @Query("select tap.id from TrackAccessPoint tap "
            + "where (:trackId is null or tap.track.id = :trackId) and tap.id > :lastId "
            + "order by tap.id asc")
    List<Long> findIdsForRepublish(@Param("trackId") Long trackId,
                                   @Param("lastId") Long lastId,
                                   Pageable pageable);

    /** Recuento de la misma seleccion que recorre {@link #findIdsForRepublish}. */
    @Query("select count(tap.id) from TrackAccessPoint tap "
            + "where (:trackId is null or tap.track.id = :trackId)")
    long countForRepublish(@Param("trackId") Long trackId);

    /**
     * Grafo completo del evento {@code track-access-point}, en una sola sentencia: la estacion y la
     * via. El paquete de la via no entra: el payload solo lee su id ({@code executionPackageId}), y el
     * id de un proxy se lee sin inicializarlo.
     */
    @Override
    @EntityGraph(attributePaths = {
            "station",
            "track"
    })
    @Query("select tap from TrackAccessPoint tap where tap.id = :id")
    Optional<TrackAccessPoint> findByIdForMessaging(@Param("id") Long id);
}
