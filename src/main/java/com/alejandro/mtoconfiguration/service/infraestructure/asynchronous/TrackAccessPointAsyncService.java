package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackAccessPointFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackAccessPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TrackAccessPointAsyncService
        extends BaseAsyncService<TrackAccessPointDTO, TrackAccessPoint, TrackAccessPointService> {

    private final TrackAccessPointService trackAccessPointService;

    @Override
    protected TrackAccessPointService getService() {
        return trackAccessPointService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<TrackAccessPointDTO>> getTrackAccessPointsAsync(Pageable pageable,
                                                                                 TrackAccessPointFilter filter) {
        return CompletableFuture.completedFuture(trackAccessPointService.getTrackAccessPoints(pageable, filter));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<TrackAccessPointDTO>> getTrackAccessPointsByTrackIdAsync(Long trackId) {
        return CompletableFuture.completedFuture(trackAccessPointService.getTrackAccessPointsByTrackId(trackId));
    }
}
