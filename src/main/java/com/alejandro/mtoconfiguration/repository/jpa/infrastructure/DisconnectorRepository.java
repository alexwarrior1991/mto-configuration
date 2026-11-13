package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import java.util.Collection;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
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
public interface DisconnectorRepository extends CRUDRepository<Disconnector>,
        MessagingEntityGraphRepository<Disconnector>, JpaSpecificationExecutor<Disconnector> {
    List<Disconnector> findByStationId(Long stationId);
    List<Disconnector> findByStationNameContainingIgnoreCase(String stationName);

    /**
     * Clave natural del seccionador para el importador del maestro: su nombre dentro de su paquete.
     * El maestro no trae identificadores técnicos, y sin ella cada carga duplicaría todos los
     * seccionadores.
     *
     * <p>No es la estación y el nombre, como la del aislador ({@code SectionInsulatorRepository}),
     * porque la estación de un seccionador es opcional: uno en plena vía, en una zona neutra o en una
     * subestación no es de ninguna. El paquete sale de la estación, de la vía del poste o de la vía
     * propia (V26), lo que tenga. Devuelve una lista: si el nombre se repite en el paquete, el
     * importador no elige uno y lo dice.
     */
    @Query("""
            select d from Disconnector d
            left join d.station s
            left join d.profile p
            left join p.track pt
            left join d.track t
            where upper(d.name) = upper(:name)
              and (s.executionPackage.id = :packageId
                   or pt.executionPackage.id = :packageId
                   or t.executionPackage.id = :packageId)
            order by d.id
            """)
    List<Disconnector> findByNameInPackage(@Param("name") String name, @Param("packageId") Long packageId);

    /**
     * Los paquetes a los que pertenece un seccionador, por los mismos tres caminos por los que lo
     * busca {@link #findByNameInPackage}: su estación, la vía de su poste y su vía propia. Lo usa
     * {@code DisconnectorNaturalKeyGuard} para preguntar si su nombre ya está en alguno de ellos.
     */
    @Query("""
            select s.executionPackage.id as stationPackageId,
                   pt.executionPackage.id as poleTrackPackageId,
                   t.executionPackage.id as trackPackageId
            from Disconnector d
            left join d.station s
            left join d.profile p
            left join p.track pt
            left join d.track t
            where d.id = :id
            """)
    Optional<PackageIds> findPackageIdsById(@Param("id") Long id);

    /** Proyección de {@link #findPackageIdsById(Long)}. */
    interface PackageIds {
        Long getStationPackageId();

        Long getPoleTrackPackageId();

        Long getTrackPackageId();
    }

    /**
     * La estación, el poste, la vía propia, la vía conectada y la función del seccionador, sin
     * inicializar nada.
     *
     * <p>Por la misma trampa que {@code SectionInsulatorRepository.findTrackIdsById}: el importador
     * no abre transacción, la entidad que devuelve la búsqueda por clave natural llega
     * <b>detached</b> y tocar ahí un {@code LAZY} revienta con {@code LazyInitializationException}.
     * Con {@code left join}, porque todos son opcionales: hay seccionadores sin estación, con poste
     * solo hay vía propia si maniobra otra vía que la del poste, sin él no hay poste, y solo uno que
     * pone dos vías en paralelo tiene vía conectada.
     */
    @Query("select s.id as stationId, p.id as profileId, t.id as trackId, ct.id as connectedTrackId, "
            + "f.id as disconnectorFunctionId "
            + "from Disconnector d left join d.station s left join d.profile p left join d.track t "
            + "left join d.connectedTrack ct "
            + "left join d.disconnectorFunction f where d.id = :id")
    Optional<LinkIds> findLinkIdsById(@Param("id") Long id);

    /** Proyección de {@link #findLinkIdsById(Long)}. */
    interface LinkIds {
        Long getStationId();

        Long getProfileId();

        Long getTrackId();

        Long getConnectedTrackId();

        Long getDisconnectorFunctionId();
    }

    /**
     * Los nombres de los seccionadores vivos que cuelgan de un poste (V29: puede llevar varios). El
     * importador los compara con su hoja para no dejar dos con nombres distintos donde habia uno
     * renombrado.
     */
    @Query("select d.name from Disconnector d where d.profile.id = :profileId order by upper(d.name)")
    List<String> findNamesOnPole(@Param("profileId") Long profileId);

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
    @Query("select d.id from Disconnector d "
            + "where (:stationId is null or d.station.id = :stationId) and d.id > :lastId "
            + "order by d.id asc")
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
    @Query("select count(d.id) from Disconnector d "
            + "where (:stationId is null or d.station.id = :stationId)")
    long countForRepublish(@Param("stationId") Long stationId);

    /**
     * {@code profile.track} no entra: DisconnectorMasterDataPayloadMapper no lee la via del
     * perfil. {@code track} si: es la del propio seccionador cuando no esta en un poste (V26), y el
     * payload lleva su nombre. {@code connectedTrack} tambien, por lo mismo (V27).
     */
    /**
     * Los seccionadores cuyo evento lleva el <b>nombre</b> de esta via: la suya propia ({@code track})
     * o la conectada. El de uno en un poste no nombra la via del poste.
     */
    @Query("select d.id from Disconnector d "
            + "where d.track.id = :trackId or d.connectedTrack.id = :trackId order by d.id asc")
    List<Long> findIdsNamingTrack(@Param("trackId") Long trackId);

    /**
     * Los seccionadores que <b>estan</b> en esta via, y por tanto en su paquete: en uno de sus postes,
     * o sin poste y con ella como via propia.
     */
    @Query("select d.id from Disconnector d left join d.profile p "
            + "where p.track.id = :trackId or d.track.id = :trackId order by d.id asc")
    List<Long> findIdsLocatedOnTrack(@Param("trackId") Long trackId);

    @Query("select d.id from Disconnector d where d.station.id = :stationId order by d.id asc")
    List<Long> findIdsByStationId(@Param("stationId") Long stationId);

    /** Un lote con el mismo grafo que {@link #findByIdForMessaging}, en una sentencia. */
    @EntityGraph(attributePaths = {
            "station",
            "profile",
            "track",
            "connectedTrack",
            "disconnectorFunction"
    })
    @Query("select d from Disconnector d where d.id in :ids order by d.id asc")
    List<Disconnector> findAllByIdForMessaging(@Param("ids") Collection<Long> ids);

    @Override
    @EntityGraph(attributePaths = {
            "station",
            "profile",
            "track",
            "connectedTrack",
            "disconnectorFunction"
    })
    @Query("select d from Disconnector d where d.id = :id")
    Optional<Disconnector> findByIdForMessaging(@Param("id") Long id);
}
