package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.DisconnectorFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.DisconnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class DisconnectorAsyncService extends BaseAsyncService<DisconnectorDTO, Disconnector, DisconnectorService> {

    private final DisconnectorService disconnectorService;

    @Override
    protected DisconnectorService getService() {
        return disconnectorService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<DisconnectorDTO>> getDisconnectorsAsync(Pageable pageable, DisconnectorFilter filter) {
        return CompletableFuture.completedFuture(disconnectorService.getDisconnectors(pageable, filter));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<DisconnectorDTO>> getDisconnectorByStationIdAsync(Long stationId) {
        return CompletableFuture.completedFuture(disconnectorService.getDisconnectorByStationId(stationId));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<DisconnectorDTO>> getDisconnectorsByStationNameAsync(String stationName) {
        return CompletableFuture.completedFuture(disconnectorService.getDisconnectorsByStationName(stationName));
    }
}
