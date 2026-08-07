package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.service.lov.FoundationService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/foundations")
@Tag(name = "Foundations", description = "Configuration LOV operations for foundations")
public class FoundationController extends AbstractLovController<FoundationDTO> {

    private final FoundationService foundationService;

    @Override
    protected FoundationService getService() {
        return foundationService;
    }

    @Override
    protected String getResourceName() {
        return "Foundation";
    }
}
