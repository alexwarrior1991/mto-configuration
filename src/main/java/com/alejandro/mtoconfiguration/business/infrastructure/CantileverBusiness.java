package com.alejandro.mtoconfiguration.business.infrastructure;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import org.springframework.stereotype.Component;

@Component
public class CantileverBusiness extends CRUDBusiness<CantileverDTO, Cantilever> {
}
