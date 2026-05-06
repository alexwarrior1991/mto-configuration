package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IDisconnectorService {

    Page<DisconnectorDTO> getDisconnectors(Pageable pageable, DisconnectorFilter filter);
    List<DisconnectorDTO> getDisconnectorByStationId(Long stationId);
    List<DisconnectorDTO> getDisconnectorsByStationName(String stationName);
}
