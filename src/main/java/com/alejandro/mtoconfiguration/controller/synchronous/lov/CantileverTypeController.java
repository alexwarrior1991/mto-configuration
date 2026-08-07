package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.CantileverTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/cantilever-types")
@Tag(name = "Cantilever Types", description = "Configuration LOV operations for cantilever types")
public class CantileverTypeController extends AbstractLovController<CantileverTypeDTO> {

    private final CantileverTypeService cantileverTypeService;

    @Override
    protected LovCrudService<CantileverTypeDTO> getService() {
        return cantileverTypeService;
    }

    @Override
    protected String getResourceName() {
        return "CantileverType";
    }
}