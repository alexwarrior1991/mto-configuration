package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ReturnSupportDTO;
import com.alejandro.mtoconfiguration.service.lov.ReturnSupportService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/return-supports")
@Tag(name = "Return Supports", description = "Configuration LOV operations for return supports")
public class ReturnSupportController extends AbstractLovController<ReturnSupportDTO> {

    private final ReturnSupportService returnSupportService;

    @Override
    protected LovCrudService<ReturnSupportDTO> getService() {
        return returnSupportService;
    }

    @Override
    protected String getResourceName() {
        return "ReturnSupport";
    }
}