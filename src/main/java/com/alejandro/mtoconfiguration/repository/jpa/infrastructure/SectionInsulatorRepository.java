package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionInsulatorRepository extends CRUDRepository<SectionInsulator> {

    List<SectionInsulator> findByStationId(Long stationId);
    List<SectionInsulator> findByStationNameContainingIgnoreCase(String stationName);
}
