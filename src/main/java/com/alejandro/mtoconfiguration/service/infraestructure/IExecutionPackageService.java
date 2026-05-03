package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ExecutionPackageFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IExecutionPackageService {

    Page<ExecutionPackageDTO> getExecutionPackages(Pageable pageable, ExecutionPackageFilter filter);
}
