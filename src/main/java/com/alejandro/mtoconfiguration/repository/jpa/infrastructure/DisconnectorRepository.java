package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisconnectorRepository extends CRUDRepository<Disconnector>,
        MessagingEntityGraphRepository<Disconnector> {
    List<Disconnector> findByStationId(Long stationId);
    List<Disconnector> findByStationNameContainingIgnoreCase(String stationName);

    /**
     * {@code profile.track} no entra: DisconnectorMasterDataPayloadMapper no lee nada
     * de la via.
     */
    @Override
    @EntityGraph(attributePaths = {
            "station",
            "profile",
            "disconnectorFunction"
    })
    @Query("select d from Disconnector d where d.id = :id")
    Optional<Disconnector> findByIdForMessaging(@Param("id") Long id);
}
