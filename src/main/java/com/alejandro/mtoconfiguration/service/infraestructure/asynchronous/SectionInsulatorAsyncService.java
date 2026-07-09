package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SectionInsulatorFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.SectionInsulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SectionInsulatorAsyncService extends BaseAsyncService<SectionInsulatorDTO, SectionInsulator, SectionInsulatorService> {

    private final SectionInsulatorService sectionInsulatorService;

    @Override
    protected SectionInsulatorService getService() {
        return sectionInsulatorService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<SectionInsulatorDTO>> getSectionInsulatorsAsync(Pageable pageable, SectionInsulatorFilter filter) {
        return CompletableFuture.completedFuture(sectionInsulatorService.getSectionInsulators(pageable, filter));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<SectionInsulatorDTO>> getSectionInsulatorsByStationIdAsync(Long stationId) {
        return CompletableFuture.completedFuture(sectionInsulatorService.getSectionInsulatorsByStationId(stationId));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<SectionInsulatorDTO>> getSectionInsulatorsByStationNameAsync(String stationName) {
        return CompletableFuture.completedFuture(sectionInsulatorService.getSectionInsulatorsByStationName(stationName));
    }
}
