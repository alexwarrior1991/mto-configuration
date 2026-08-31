package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackRepository extends CRUDRepository<Track>,
        MessagingEntityGraphRepository<Track> {

    /**
     * {@code profiles.disconnector} no lo pide TrackMasterDataPayloadMapper, lo impone
     * Hibernate: Profile.disconnector es el lado INVERSO de un {@code @OneToOne} y no
     * puede ser perezoso sin bytecode enhancement, asi que si no se trae en el grafo
     * se carga con un select secundario POR CADA perfil de la via. Una via con 200
     * perfiles hacia 201 consultas para publicar un evento; con esta ruta, una.
     * <p>
     * Es un join a-uno colgando de profiles, no una segunda coleccion: no multiplica
     * filas.
     */
    @Override
    @EntityGraph(attributePaths = {
            "executionPackage",
            "station",
            "profiles",
            "profiles.disconnector"
    })
    @Query("select t from Track t where t.id = :id")
    Optional<Track> findByIdForMessaging(@Param("id") Long id);
}
