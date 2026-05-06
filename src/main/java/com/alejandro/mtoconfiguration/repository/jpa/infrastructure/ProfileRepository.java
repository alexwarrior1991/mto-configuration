package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProfileRepository extends CRUDRepository<Profile> {
    List<Profile> findByTrackId(Long trackId);

    List<Profile> findByTrackNameContainingIgnoreCase(String trackName);

    List<Profile> findByTrackStationNameContainingIgnoreCase(String stationName);

    // Método para la primera página (o búsqueda normal)
    // 1. Añade JOIN FETCH a la consulta de la primera página
    @Query("select p from Profile p " +
            "left join fetch p.track " +
            "left join fetch p.foundation " +
            "left join fetch p.poleType " +
            "left join fetch p.cantilevers " + // <--- Nueva optimización
            "where p.track.id = :trackId " +
            "order by p.kp, p.id asc")
    List<Profile> findByTrackIdOrderByKpAscIdAsc(@Param("trackId") Long trackId, Pageable pageable);

    // Método Keyset: Busca después del último KP e ID procesado
    @Query("select p from Profile p " +
            "left join fetch p.track " +
            "left join fetch p.foundation " +
            "left join fetch p.poleType " +
            "left join fetch p.cantilevers " + // <--- Nueva optimización
            "where p.track.id = :trackId and " +
            "(p.kp > :lastKp or (p.kp = :lastKp and p.id > :lastId)) " +
            "order by p.kp, p.id asc")
    List<Profile> findNextPage(@Param("trackId") Long trackId,
                               @Param("lastKp") BigDecimal lastKp,
                               @Param("lastId") Long lastId,
                               Pageable pageable);


    @EntityGraph(value = "Profile.export", type = EntityGraph.EntityGraphType.FETCH)
    @Query("select p from Profile p where p.track.id = :trackId order by p.kp, p.id asc")
    List<Profile> findByTrackIdOrderByKpAscIdAscGraph(@Param("trackId") Long trackId, Pageable pageable);


    @EntityGraph(value = "Profile.export", type = EntityGraph.EntityGraphType.FETCH)
    @Query("select p from Profile p " +
            "where p.track.id = :trackId and " +
            "(p.kp > :lastKp or (p.kp = :lastKp and p.id > :lastId)) " +
            "order by p.kp, p.id asc")
    List<Profile> findNextPageGraph(@Param("trackId") Long trackId,
                               @Param("lastKp") BigDecimal lastKp,
                               @Param("lastId") Long lastId,
                               Pageable pageable);

    @Query("SELECT p FROM Profile p WHERE p.track.id = :trackId AND " +
            "p.kp BETWEEN :startKp AND :endKp " +
            "ORDER BY p.kp ASC, p.id ASC")
    List<Profile> findByKpRange(@Param("trackId") Long trackId,
                                @Param("startKp") BigDecimal startKp,
                                @Param("endKp") BigDecimal endKp);


}
