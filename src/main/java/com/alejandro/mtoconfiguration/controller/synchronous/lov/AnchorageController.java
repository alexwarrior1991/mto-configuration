package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import com.alejandro.mtoconfiguration.service.lov.AnchorageService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/anchorages")
@Tag(name = "Anchorage", description = "Configuration LOV operations for anchorage records")
public class AnchorageController extends AbstractLovController<AnchorageDTO> {

    private final AnchorageService anchorageService;

    @Override
    protected AnchorageService getService() {
        return anchorageService;
    }

    @Override
    protected String getResourceName() {
        return "Anchorage";
    }
}
