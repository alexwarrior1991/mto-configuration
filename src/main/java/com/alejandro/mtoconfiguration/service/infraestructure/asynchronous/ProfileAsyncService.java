package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileAsyncService extends BaseAsyncService<ProfileDTO, Profile, ProfileService> {

    private final ProfileService profileService;

    @Override
    protected ProfileService getService() {
        return profileService;
    }
}
