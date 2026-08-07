package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationTypeDTO;
import com.alejandro.mtoconfiguration.service.lov.AnchorageFoundationTypeService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/anchorage-foundation-types")
@Tag(name = "Anchorage Foundation Types", description = "Configuration LOV operations for anchorage foundation types")
public class AnchorageFoundationTypeController extends AbstractLovController<AnchorageFoundationTypeDTO> {

    private final AnchorageFoundationTypeService anchorageFoundationTypeService;

    @Override
    protected LovCrudService<AnchorageFoundationTypeDTO> getService() {
        return anchorageFoundationTypeService;
    }

    @Override
    protected String getResourceName() {
        return "AnchorageFoundationType";
    }
}