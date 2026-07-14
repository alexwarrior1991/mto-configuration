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

    @Override
    @EntityGraph(attributePaths = {
            "company",
            "tracks",
            "tracks.station",
            "stations"
    })
    @Query("select e from ExecutionPackage e where e.id = :id")
    Optional<ExecutionPackage> findByIdForMessaging(@Param("id") Long id);
}
