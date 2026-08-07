package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalDTO;
import com.alejandro.mtoconfiguration.service.lov.PortalService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/portals")
@Tag(name = "Portals", description = "Configuration LOV operations for portals")
public class PortalController extends AbstractLovController<PortalDTO> {

    private final PortalService portalService;

    @Override
    protected PortalService getService() {
        return portalService;
    }

    @Override
    protected String getResourceName() {
        return "Portal";
    }
}