package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.CantileverFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.CantileverService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CantileverAsyncService extends BaseAsyncService<CantileverDTO, Cantilever, CantileverService> {

    private final CantileverService cantileverService;

    @Override
    protected CantileverService getService() {
        return cantileverService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<CantileverDTO>> getCantileversAsync(Pageable pageable, CantileverFilter filter) {
        return CompletableFuture.completedFuture(cantileverService.getCantilevers(pageable, filter));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<CantileverDTO>> getByProfileIdAsync(Long profileId) {
        return CompletableFuture.completedFuture(cantileverService.getByProfileId(profileId));
    }

}
