package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.SteadyArmTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/steady-arm-types")
@Tag(name = "Steady Arm Types", description = "Configuration LOV operations for steady arm types")
public class SteadyArmTypeController extends AbstractLovController<SteadyArmTypeDTO> {

    private final SteadyArmTypeService steadyArmTypeService;

    @Override
    protected LovCrudService<SteadyArmTypeDTO> getService() {
        return steadyArmTypeService;
    }

    @Override
    protected String getResourceName() {
        return "SteadyArmType";
    }
}