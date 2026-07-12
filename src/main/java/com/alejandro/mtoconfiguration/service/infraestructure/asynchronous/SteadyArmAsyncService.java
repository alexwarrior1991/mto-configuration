package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.SteadyArmFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.SteadyArmService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SteadyArmAsyncService extends BaseAsyncService<SteadyArmDTO, SteadyArm, SteadyArmService> {

    private final SteadyArmService steadyArmService;

    @Override
    protected SteadyArmService getService() {
        return steadyArmService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<SteadyArmDTO>> getSteadyArmsAsync(Pageable pageable, SteadyArmFilter filter) {
        return CompletableFuture.completedFuture(steadyArmService.getSteadyArms(pageable, filter));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Optional<SteadyArmDTO>> getByCantileverIdAsync(Long cantileverId) {
        return CompletableFuture.completedFuture(steadyArmService.getByCantileverId(cantileverId));
    }

}
