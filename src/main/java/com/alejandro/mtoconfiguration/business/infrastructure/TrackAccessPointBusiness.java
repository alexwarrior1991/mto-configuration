package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import org.springframework.stereotype.Component;

@Component
public class TrackAccessPointBusiness extends CRUDBusiness<TrackAccessPointDTO, TrackAccessPoint> {
}
