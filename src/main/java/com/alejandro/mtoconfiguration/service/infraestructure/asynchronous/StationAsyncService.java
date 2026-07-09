package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.StationFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class StationAsyncService extends BaseAsyncService<StationDTO, Station, StationService> {

    private final StationService stationService;

    @Override
    protected StationService getService() {
        return stationService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<StationDTO>> getStationsAsync(Pageable pageable, StationFilter filter) {
        return CompletableFuture.completedFuture(stationService.getStations(pageable, filter));
    }
}
