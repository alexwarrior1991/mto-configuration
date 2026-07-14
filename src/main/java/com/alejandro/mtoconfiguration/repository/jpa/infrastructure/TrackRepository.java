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

    @Override
    @EntityGraph(attributePaths = {
            "executionPackage",
            "station",
            "profiles"
    })
    @Query("select t from Track t where t.id = :id")
    Optional<Track> findByIdForMessaging(@Param("id") Long id);
}
