package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.FoundationTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/foundation-types")
@Tag(name = "Foundation Types", description = "Configuration LOV operations for foundation types")
public class FoundationTypeController extends AbstractLovController<FoundationTypeDTO> {

    private final FoundationTypeService foundationTypeService;

    @Override
    protected FoundationTypeService getService() {
        return foundationTypeService;
    }

    @Override
    protected String getResourceName() {
        return "FoundationType";
    }
}
