package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import com.alejandro.mtoconfiguration.utils.ValidatorUtils;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Collections;
import java.util.List;

@Component
@RequestScope
@Slf4j
public class StationValidator extends NormalEntityValidator<StationDTO> {

    private static final String ENTITY_NAME = "station";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_EXECUTION_PACKAGE_ID = "executionPackageId";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(StationDTO dto, List<Alert> alerts) {
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredFieldIfTrueCondition(
                        Utils.exists(dto),
                        dto.getExecutionPackageId(),
                        ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        FIELD_EXECUTION_PACKAGE_ID
                )
                .validateLengthField(dto.getName(), 1, 100, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);
    }
}
