package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import org.springframework.stereotype.Component;

@Component
public class SectionInsulatorBusiness extends CRUDBusiness<SectionInsulatorDTO, SectionInsulator> {
}
