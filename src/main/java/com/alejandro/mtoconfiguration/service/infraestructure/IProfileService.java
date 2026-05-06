package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.ProfileFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

public interface IProfileService {

    Page<ProfileDTO> getProfiles(Pageable pageable, ProfileFilter filter);

    void processProfilesByTrack(Long trackId, Consumer<Profile> processor);

    List<ProfileDTO> getProfilesByKeyset(Long trackId, BigDecimal lastKp, Long lastId, int pageSize);

    List<ProfileDTO> getProfilesByKpRange(Long trackId, BigDecimal startKp, BigDecimal endKp);
}
