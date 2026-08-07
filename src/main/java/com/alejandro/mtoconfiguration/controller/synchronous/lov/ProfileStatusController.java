package com.alejandro.mtoconfiguration.controller.synchronous.lov;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.service.lov.ProfileStatusService;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ConfigurationApiPaths.BASE_PATH + "/profile-statuses")
@Tag(name = "Profile Statuses", description = "Configuration LOV operations for profile statuses")
public class ProfileStatusController extends AbstractLovController<ProfileStatusDTO> {

    private final ProfileStatusService profileStatusService;

    @Override
    protected LovCrudService<ProfileStatusDTO> getService() {
        return profileStatusService;
    }

    @Override
    protected String getResourceName() {
        return "ProfileStatus";
    }
}