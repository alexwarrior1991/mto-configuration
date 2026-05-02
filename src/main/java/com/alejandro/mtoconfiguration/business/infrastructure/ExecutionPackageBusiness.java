package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPackageBusiness extends CRUDBusiness<ExecutionPackageDTO, ExecutionPackage> {
}
