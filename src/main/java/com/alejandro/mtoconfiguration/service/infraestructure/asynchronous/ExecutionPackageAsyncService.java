package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ExecutionPackageAsyncService extends BaseAsyncService<ExecutionPackageDTO, ExecutionPackage, ExecutionPackageService> {

    private final ExecutionPackageService executionPackageService;

    @Override
    protected ExecutionPackageService getService() {
        return executionPackageService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<ExecutionPackageDTO>> getExecutionPackagesAsync(Pageable pageable, ExecutionPackageFilter filter) {
        return CompletableFuture.completedFuture(executionPackageService.getExecutionPackages(pageable, filter));
    }
}
