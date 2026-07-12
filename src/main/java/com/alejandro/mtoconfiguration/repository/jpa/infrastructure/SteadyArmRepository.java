package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SteadyArmRepository extends CRUDRepository<SteadyArm> {
    Optional<SteadyArm> findByCantileverId(Long cantileverId);
}
