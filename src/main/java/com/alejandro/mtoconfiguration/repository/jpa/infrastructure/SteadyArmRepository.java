package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SteadyArmRepository extends CRUDRepository<SteadyArm>, MessagingEntityGraphRepository<SteadyArm> {
    Optional<SteadyArm> findByCantileverId(Long cantileverId);

    @Override
    @EntityGraph(attributePaths = {
            "steadyArmType"
    })
    @Query("select s from SteadyArm s where s.id = :id")
    Optional<SteadyArm> findByIdForMessaging(@Param("id") Long id);
}
