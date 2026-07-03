package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StationAsyncService extends BaseAsyncService<StationDTO, Station, StationService> {

    private final StationService stationService;

    @Override
    protected StationService getService() {
        return stationService;
    }
}
