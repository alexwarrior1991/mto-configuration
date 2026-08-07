package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SupportTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.SupportTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/support-types")
@Tag(name = "Support Types", description = "Configuration LOV operations for support types")
public class SupportTypeController extends AbstractLovController<SupportTypeDTO> {

    private final SupportTypeService supportTypeService;

    @Override
    protected SupportTypeService getService() {
        return supportTypeService;
    }

    @Override
    protected String getResourceName() {
        return "SupportType";
    }
}
