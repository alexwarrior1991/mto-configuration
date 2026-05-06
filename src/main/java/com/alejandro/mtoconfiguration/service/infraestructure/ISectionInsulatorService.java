package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ISectionInsulatorService {

    Page<SectionInsulatorDTO> getSectionInsulators(Pageable pageable, SectionInsulatorFilter filter);

    List<SectionInsulatorDTO> getSectionInsulatorsByStationId(Long stationId);

    List<SectionInsulatorDTO> getSectionInsulatorsByStationName(String stationName);
}
