package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SteadyArmFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ISteadyArmService {

    Page<SteadyArmDTO> getSteadyArms(Pageable pageable, SteadyArmFilter filter);

    Optional<SteadyArmDTO> getByCantileverId(Long cantileverId);
}
