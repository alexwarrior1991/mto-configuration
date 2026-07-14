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

    @Override
    @EntityGraph(attributePaths = {
            "executionPackage",
            "tracks",
            "disconnectors",
            "disconnectors.profile",
            "disconnectors.disconnectorFunction",
            "sectionInsulators"
    })
    @Query("select s from Station s where s.id = :id")
    Optional<Station> findByIdForMessaging(@Param("id") Long id);
}
