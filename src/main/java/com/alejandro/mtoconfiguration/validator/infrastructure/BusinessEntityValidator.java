package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.BusinessEntityDTO;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Collections;
import java.util.List;

@Component
@RequestScope
@Slf4j
public class BusinessEntityValidator extends CRUDValidator<BusinessEntityDTO> {

    @Override
    public List<Alert> validateBeforeDelete(BusinessEntityDTO dto) {
        getAlerts().clear();
        return Collections.emptyList();
    }

    @Override
    protected void loadRequireFields() {
        super.getRequiredFields();
    }

    @Override
    public List<Alert> validateBeforeSave(BusinessEntityDTO dto) {
        return List.of();
    }
}
