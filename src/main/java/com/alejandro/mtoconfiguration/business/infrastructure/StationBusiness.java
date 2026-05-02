package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import org.springframework.stereotype.Component;

@Component
public class StationBusiness extends CRUDBusiness<StationDTO, Station> {
}
