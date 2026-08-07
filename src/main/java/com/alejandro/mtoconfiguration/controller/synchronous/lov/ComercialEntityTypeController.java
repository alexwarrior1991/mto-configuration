package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ComercialEntityTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.ComercialEntityTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/comercial-entity-types")
@Tag(name = "Comercial Entity Types", description = "Configuration LOV operations for comercial entity types")
public class ComercialEntityTypeController extends AbstractLovController<ComercialEntityTypeDTO> {

    private final ComercialEntityTypeService comercialEntityTypeService;

    @Override
    protected LovCrudService<ComercialEntityTypeDTO> getService() {
        return comercialEntityTypeService;
    }

    @Override
    protected String getResourceName() {
        return "ComercialEntityType";
    }
}