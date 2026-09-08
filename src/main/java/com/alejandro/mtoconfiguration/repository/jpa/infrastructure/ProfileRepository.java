package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends CRUDRepository<Profile>,
        MessagingEntityGraphRepository<Profile> {
    List<Profile> findByTrackId(Long trackId);

    /**
     * Busqueda por la clave natural, para el find-or-create del importador.
     *
     * <p>Ignora mayusculas porque el origen no es consistente ({@code HR TRACK 3 HAD} y
     * {@code HR Track 3 BIN} conviven en el mismo workbook) y porque es lo que indexa
     * {@code ux_profile_track_profile_id} (V12). El borrado logico lo filtra la
     * {@code @SQLRestriction} de {@code CRUDEntity}, igual que ese indice parcial.
     */
    Optional<Profile> findByTrackIdAndProfileIdIgnoreCase(Long trackId, String profileId);

    List<Profile> findByTrackNameContainingIgnoreCase(String trackName);

    List<Profile> findByTrackStationsNameContainingIgnoreCase(String stationName);

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



    /**
     * {@code cantilevers.steadyArm} y {@code disconnector} son obligatorios: son el
     * lado INVERSO de un {@code @OneToOne}, que Hibernate no puede proxear, asi que
     * sin ellos se paga un select por fila.
     * <p>
     * {@code track.stations} entra desde V17 por lo mismo: dejo de ser un {@code @ManyToOne}
     * del que basta el id del proxy y paso a ser una coleccion, que desatachada no se puede
     * recorrer.
     * <p>
     * Las rutas anidadas de las que el mapper solo lee el id se quedan fuera
     * ({@code track.executionPackage},
     * {@code cantilevers.cantileverType}, {@code cantilevers.steadyArm.steadyArmType}
     * y {@code disconnector.disconnectorFunction}): con acceso por propiedad, leer el
     * id de un proxy no lo inicializa.
     * <p>
     * El grafo trae VARIAS colecciones ({@code cantilevers} y las tres N:M de LOVs de
     * V14-V16, mas {@code track.stations} desde V17), asi que el join produce un producto
     * cartesiano. Se acepta porque los factores son diminutos —un perfil tiene tres mensulas
     * como mucho y un puñado de codigos por lista— y la alternativa es un select por
     * coleccion o, peor, una LazyInitializationException al publicar el evento.
     */
    @Override
    @EntityGraph(attributePaths = {
            "track",
            "track.stations",
            "anchorages",
            "anchorageFoundation",
            "foundation",
            "poleType",
            "portal",
            "profileStatus",
            "returnSupport",
            "sectionings",
            "sectioningFeedings",
            "cantilevers",
            "cantilevers.steadyArm",
            "disconnector"
    })
    @Query("select p from Profile p where p.id = :id")
    Optional<Profile> findByIdForMessaging(@Param("id") Long id);


}
