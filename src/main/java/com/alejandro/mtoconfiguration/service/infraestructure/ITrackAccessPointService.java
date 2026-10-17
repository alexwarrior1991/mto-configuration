package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter.TrackAccessPointFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ITrackAccessPointService {

    Page<TrackAccessPointDTO> getTrackAccessPoints(Pageable pageable, TrackAccessPointFilter filter);

    List<TrackAccessPointDTO> getTrackAccessPointsByTrackId(Long trackId);
}
