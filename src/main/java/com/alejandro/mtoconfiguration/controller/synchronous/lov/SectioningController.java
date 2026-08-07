package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.service.lov.SectioningService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/sectionings")
@Tag(name = "Sectioning", description = "Configuration LOV operations for sectioning records")
public class SectioningController extends AbstractLovController<SectioningDTO> {

    private final SectioningService sectioningService;

    @Override
    protected SectioningService getService() {
        return sectioningService;
    }

    @Override
    protected String getResourceName() {
        return "Sectioning";
    }
}

