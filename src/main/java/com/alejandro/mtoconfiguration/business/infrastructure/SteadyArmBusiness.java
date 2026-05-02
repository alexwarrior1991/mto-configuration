package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import org.springframework.stereotype.Component;

@Component
public class SteadyArmBusiness extends CRUDBusiness<SteadyArmDTO, SteadyArm> {
}
