package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExecutionPackageRepository extends
        CRUDRepository<ExecutionPackage>, MessagingEntityGraphRepository<ExecutionPackage> {

    /**
     * Mismo motivo que en StationRepository: ExecutionPackageMasterDataPayloadMapper
     * lee dos colecciones (tracks y stations) y en un unico {@code @EntityGraph}
     * Hibernate las une en la misma sentencia, multiplicando las filas entre si. Un
     * paquete con 200 vias y 30 estaciones son 6.000 filas para publicar un evento.
     * <p>
     * Una consulta por coleccion: ambas devuelven la MISMA instancia gestionada del
     * contexto de persistencia, asi que la entidad queda igual de completa con filas
     * planas.
     */
    @Override
    default Optional<ExecutionPackage> findByIdForMessaging(Long id) {
        Optional<ExecutionPackage> executionPackage = findByIdWithTracksForMessaging(id);

        if (executionPackage.isPresent()) {
            findByIdWithStationsForMessaging(id);
        }

        return executionPackage;
    }

    /**
     * {@code tracks.station} no entra: el mapper solo publica su id y Track es el lado
     * propietario (la FK STATION_ID vive en TRACK), asi que sale del proxy sin
     * inicializarlo.
     */
    @EntityGraph(attributePaths = {
            "company",
            "tracks"
    })
    @Query("select e from ExecutionPackage e where e.id = :id")
    Optional<ExecutionPackage> findByIdWithTracksForMessaging(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "stations"
    })
    @Query("select e from ExecutionPackage e where e.id = :id")
    Optional<ExecutionPackage> findByIdWithStationsForMessaging(@Param("id") Long id);
}
