package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
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
public class SteadyArmValidator extends NormalEntityValidator<SteadyArmDTO> {

    private static final String ENTITY_NAME = "steadyArm";
    private static final String FIELD_LENGTH = "length";
    private static final String FIELD_STEADY_ARM_TYPE = "steadyArmType";
    private static final String FIELD_CANTILEVER_ID = "cantileverId";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(SteadyArmDTO dto, List<Alert> alerts) {
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getLength(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_LENGTH)
                .validateRequiredLovDTO(dto.getSteadyArmType(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STEADY_ARM_TYPE)
                .validateRequiredFieldIfTrueCondition(
                        Utils.exists(dto),
                        dto.getCantileverId(),
                        ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        FIELD_CANTILEVER_ID
                )
                .validateRange(
                        dto.getLength(),
                        1L,
                        999999L,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE,
                        FIELD_LENGTH
                );
    }

}
