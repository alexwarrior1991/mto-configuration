package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ITrackService {

    Page<TrackDTO> getTracks(Pageable pageable, TrackFilter filter);
}
