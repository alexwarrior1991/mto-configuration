package com.alejandro.mtoconfiguration.service.infraestructure.asynchronous;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import com.alejandro.mtoconfiguration.service.commons.BaseAsyncService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ProfileAsyncService extends BaseAsyncService<ProfileDTO, Profile, ProfileService> {

    private final ProfileService profileService;

    @Override
    protected ProfileService getService() {
        return profileService;
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<ProfileDTO>> getProfilesAsync(Pageable pageable, ProfileFilter filter) {
        return CompletableFuture.completedFuture(profileService.getProfiles(pageable, filter));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<ProfileDTO>> getProfilesByKeysetAsync(Long trackId, BigDecimal lastKp, Long lastId, int pageSize) {
        return CompletableFuture.completedFuture(profileService.getProfilesByKeyset(trackId, lastKp, lastId, pageSize));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<ProfileDTO>> getProfilesByKpRangeAsync(Long trackId, BigDecimal startKp, BigDecimal endKp) {
        return CompletableFuture.completedFuture(profileService.getProfilesByKpRange(trackId, startKp, endKp));
    }
}
