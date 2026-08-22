package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.BusinessEntityDTO;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * La entidad de negocio llega desde el maestro externo, no se da de alta desde este servicio: no
 * hay reglas propias que comprobar.
 */
@Component
public class BusinessEntityValidator extends CRUDValidator<BusinessEntityDTO> {

    private static final String ENTITY_NAME = "businessEntity";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    public List<Alert> validateBeforeSave(BusinessEntityDTO dto) {
        return Collections.emptyList();
    }

    @Override
    public List<Alert> validateBeforeDelete(BusinessEntityDTO dto) {
        return Collections.emptyList();
    }
}
