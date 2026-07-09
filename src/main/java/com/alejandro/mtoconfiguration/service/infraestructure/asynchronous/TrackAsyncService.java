package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TrackAsyncService extends BaseAsyncService<TrackDTO, Track, TrackService> {

    private final TrackService trackService;

    @Override
    protected TrackService getService() {
        return trackService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<TrackDTO>> getTracksAsync(Pageable pageable, TrackFilter filter) {
        return CompletableFuture.completedFuture(trackService.getTracks(pageable, filter));
    }
}
