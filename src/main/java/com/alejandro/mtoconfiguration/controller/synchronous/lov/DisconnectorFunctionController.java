package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.service.lov.DisconnectorFunctionService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/disconnector-functions")
@Tag(name = "Disconnector Functions", description = "Configuration LOV operations for disconnector functions")
public class DisconnectorFunctionController extends AbstractLovController<DisconnectorFunctionDTO> {

    private final DisconnectorFunctionService disconnectorFunctionService;

    @Override
    protected LovCrudService<DisconnectorFunctionDTO> getService() {
        return disconnectorFunctionService;
    }

    @Override
    protected String getResourceName() {
        return "DisconnectorFunction";
    }
}