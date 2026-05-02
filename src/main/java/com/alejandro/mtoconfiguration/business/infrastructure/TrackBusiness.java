package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import org.springframework.stereotype.Component;

@Component
public class TrackBusiness extends CRUDBusiness<TrackDTO, Track> {
}
