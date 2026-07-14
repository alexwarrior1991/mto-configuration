package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
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


    @Override
    @EntityGraph(attributePaths = {
            "station"
    })
    @Query("select si from SectionInsulator si where si.id = :id")
    Optional<SectionInsulator> findByIdForMessaging(@Param("id") Long id);
}
