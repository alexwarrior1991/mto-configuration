package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StationRepository extends CRUDRepository<Station>,
        MessagingEntityGraphRepository<Station> {

    /**
     * StationMasterDataPayloadMapper lee TRES colecciones (tracks, disconnectors y
     * sectionInsulators). En un unico {@code @EntityGraph} Hibernate las resuelve como
     * tres joins en la misma sentencia, y el resultado es un producto cartesiano:
     * {@code |tracks| x |disconnectors| x |sectionInsulators|} filas. Con 10 vias, 50
     * seccionadores y 20 aisladores son 10.000 filas para publicar un solo evento, y
     * encima dentro de la transaccion de negocio, que es la que aguanta el bloqueo.
     * <p>
     * Por eso se carga una coleccion por consulta. Las tres devuelven la MISMA
     * instancia gestionada del contexto de persistencia, asi que la entidad acaba
     * igual de completa que con el grafo unico, pero con filas planas: tres consultas
     * de {@code |tracks|}, {@code |disconnectors|} y {@code |sectionInsulators|} filas
     * en lugar de una de su producto.
     */
    @Override
    default Optional<Station> findByIdForMessaging(Long id) {
        Optional<Station> station = findByIdWithTracksForMessaging(id);

        if (station.isPresent()) {
            findByIdWithDisconnectorsForMessaging(id);
            findByIdWithSectionInsulatorsForMessaging(id);
        }

        return station;
    }

    @EntityGraph(attributePaths = {
            "executionPackage",
            "tracks"
    })
    @Query("select s from Station s where s.id = :id")
    Optional<Station> findByIdWithTracksForMessaging(@Param("id") Long id);

    /**
     * Ni {@code disconnectors.profile} ni {@code disconnectors.disconnectorFunction}
     * entran: el mapper solo publica sus ids y ambos son el lado propietario, asi que
     * salen del proxy sin inicializarlo.
     */
    @EntityGraph(attributePaths = {
            "disconnectors"
    })
    @Query("select s from Station s where s.id = :id")
    Optional<Station> findByIdWithDisconnectorsForMessaging(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "sectionInsulators"
    })
    @Query("select s from Station s where s.id = :id")
    Optional<Station> findByIdWithSectionInsulatorsForMessaging(@Param("id") Long id);
}
