package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.utils.ValidatorUtils;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
@RequestScope
@Slf4j
@RequiredArgsConstructor
public class CantileverValidator extends NormalEntityValidator<CantileverDTO> {

    private static final String ENTITY_NAME = "cantilever";
    private static final String FIELD_CW_HEIGHT = "cwHeight";
    private static final String FIELD_STAGGER = "stagger";
    private static final String FIELD_CATENARY_HEIGHT = "catenaryHeight";
    private static final String FIELD_CW_ELEVATION = "cwElevation";
    private static final String FIELD_WIND_DEFLECTION = "windDeflection";
    private static final String FIELD_ARM_ANGLE = "armAngle";
    private static final String FIELD_CANTILEVER_TYPE = "cantileverType";
    private static final String FIELD_PROFILE_ID = "profileId";
    private static final String FIELD_STEADY_ARM = "steadyArm";

    private final SteadyArmValidator steadyArmValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(CantileverDTO dto, List<Alert> alerts) {
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getCwHeight(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CW_HEIGHT)
                .validateRequiredField(dto.getStagger(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STAGGER)
                .validateRequiredField(dto.getCatenaryHeight(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CATENARY_HEIGHT)
                .validateRequiredField(dto.getCwElevation(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CW_ELEVATION)
                .validateRequiredField(dto.getWindDeflection(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_WIND_DEFLECTION)
                .validateRequiredField(dto.getArmAngle(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ARM_ANGLE)
                .validateRequiredField(dto.getProfileId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_ID)
                .validateRequiredLovDTO(dto.getCantileverType(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CANTILEVER_TYPE)
                .validateRequiredDTO(dto.getSteadyArm(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STEADY_ARM)
                .validateBigDecimalWithPrecision(dto.getCwHeight(), 10, 3, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CW_HEIGHT)
                .validateBigDecimalWithPrecision(dto.getStagger(), 10, 3, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_STAGGER)
                .validateBigDecimalWithPrecision(dto.getCatenaryHeight(), 10, 3, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CATENARY_HEIGHT)
                .validateBigDecimalWithPrecision(dto.getCwElevation(), 10, 3, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CW_ELEVATION)
                .validateBigDecimalWithPrecision(dto.getWindDeflection(), 10, 3, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_WIND_DEFLECTION)
                .validateBigDecimalWithPrecision(dto.getArmAngle(), 10, 3, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_ARM_ANGLE);

        validateSteadyArm(dto, alerts);
    }

    private void validateSteadyArm(CantileverDTO dto, List<Alert> alerts) {
        if (dto.getSteadyArm() == null) {
            return;
        }

        List<Alert> steadyArmAlerts = steadyArmValidator.validateBeforeSave(dto.getSteadyArm());

        if (CollectionUtils.isEmpty(steadyArmAlerts)) {
            return;
        }

        steadyArmAlerts.stream()
                .filter(Objects::nonNull)
                .peek(alert -> alert.setFields(
                        alert.getFields().stream()
                                .map(field -> FIELD_STEADY_ARM + "." + field)
                                .toList()
                ))
                .forEach(alerts::add);
    }
}
