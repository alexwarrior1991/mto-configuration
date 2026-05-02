package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import org.springframework.stereotype.Component;

@Component
public class DisconnectorBusiness extends CRUDBusiness<DisconnectorDTO, Disconnector> {
}
