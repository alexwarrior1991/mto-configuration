package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PoleTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.PoleTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/pole-types")
@Tag(name = "Pole Types", description = "Configuration LOV operations for pole types")
public class PoleTypeController extends AbstractLovController<PoleTypeDTO> {

    private final PoleTypeService poleTypeService;

    @Override
    protected PoleTypeService getService() {
        return poleTypeService;
    }

    @Override
    protected String getResourceName() {
        return "PoleType";
    }
}
