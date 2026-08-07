package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationDTO;
import com.alejandro.mtoconfiguration.service.lov.AnchorageFoundationService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/anchorage-foundations")
@Tag(name = "Anchorage Foundations", description = "Configuration LOV operations for anchorage foundations")
public class AnchorageFoundationController extends AbstractLovController<AnchorageFoundationDTO> {

    private final AnchorageFoundationService anchorageFoundationService;

    @Override
    protected LovCrudService<AnchorageFoundationDTO> getService() {
        return anchorageFoundationService;
    }

    @Override
    protected String getResourceName() {
        return "AnchorageFoundation";
    }
}