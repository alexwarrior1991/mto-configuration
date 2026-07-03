package com.alejandro.mtoconfiguration.controller.synchronous.infraestructure;

import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/executionPackage")
public class ExecutionPackageController extends CRUDController<ExecutionPackageDTO, ExecutionPackage> {

    @Autowired
    private ExecutionPackageService service;


    @Override
    public ExecutionPackageService getService() {
        return service;
    }
}
