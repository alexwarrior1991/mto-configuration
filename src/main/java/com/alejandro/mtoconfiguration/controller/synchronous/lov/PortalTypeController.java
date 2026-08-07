package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.PortalTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/portal-types")
@Tag(name = "Portal Types", description = "Configuration LOV operations for portal types")
public class PortalTypeController extends AbstractLovController<PortalTypeDTO> {

    private final PortalTypeService portalTypeService;

    @Override
    protected PortalTypeService getService() {
        return portalTypeService;
    }

    @Override
    protected String getResourceName() {
        return "PortalType";
    }
}