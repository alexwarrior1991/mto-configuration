package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.StationFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IStationService {

    Page<StationDTO> getStations(Pageable pageable, StationFilter filter);
}
