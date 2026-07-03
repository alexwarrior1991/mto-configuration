package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutionPackageAsyncService extends BaseAsyncService<ExecutionPackageDTO, ExecutionPackage, ExecutionPackageService> {

    private final ExecutionPackageService executionPackageService;

    @Override
    protected ExecutionPackageService getService() {
        return executionPackageService;
    }
}
