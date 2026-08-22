package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CATENARY_HEIGHT_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CATENARY_HEIGHT_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_ELEVATION_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_ELEVATION_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_HEIGHT_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_HEIGHT_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_HEIGHT_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.STAGGER_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.STAGGER_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.WIND_DEFLECTION_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.WIND_DEFLECTION_INTEGER_DIGITS;

@Component
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

    private static final BigDecimal CW_HEIGHT_MIN_VALUE = new BigDecimal(CW_HEIGHT_MIN);
    private static final BigDecimal ARM_ANGLE_MIN_VALUE = new BigDecimal(ARM_ANGLE_MIN);
    private static final BigDecimal ARM_ANGLE_MAX_VALUE = new BigDecimal(ARM_ANGLE_MAX);

    private final SteadyArmValidator steadyArmValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(CantileverDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getCwHeight(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CW_HEIGHT)
                .validateRequiredField(dto.getStagger(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STAGGER)
                .validateRequiredField(dto.getCatenaryHeight(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CATENARY_HEIGHT)
                .validateRequiredField(dto.getCwElevation(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CW_ELEVATION)
                .validateRequiredField(dto.getWindDeflection(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_WIND_DEFLECTION)
                .validateRequiredField(dto.getArmAngle(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ARM_ANGLE)
                .validateRequiredLovDTO(dto.getCantileverType(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CANTILEVER_TYPE)
                // La asociación es obligatoria, pero no que ya exista: SteadyArm cascadea desde
                // Cantilever, así que una ménsula nueva (sin id) es un alta perfectamente válida.
                .validateRequiredField(dto.getSteadyArm(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STEADY_ARM)
                // Precisiones tomadas de las columnas: aceptar más aquí solo cambia el 400 por un 500.
                .validateBigDecimalWithPrecision(dto.getCwHeight(), CW_HEIGHT_INTEGER_DIGITS, CW_HEIGHT_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CW_HEIGHT)
                .validateRange(dto.getCwHeight(), CW_HEIGHT_MIN_VALUE, null,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CW_HEIGHT)
                .validateBigDecimalWithPrecision(dto.getStagger(), STAGGER_INTEGER_DIGITS, STAGGER_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_STAGGER)
                .validateBigDecimalWithPrecision(dto.getCatenaryHeight(), CATENARY_HEIGHT_INTEGER_DIGITS, CATENARY_HEIGHT_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CATENARY_HEIGHT)
                .validateBigDecimalWithPrecision(dto.getCwElevation(), CW_ELEVATION_INTEGER_DIGITS, CW_ELEVATION_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CW_ELEVATION)
                .validateBigDecimalWithPrecision(dto.getWindDeflection(), WIND_DEFLECTION_INTEGER_DIGITS, WIND_DEFLECTION_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_WIND_DEFLECTION)
                .validateBigDecimalWithPrecision(dto.getArmAngle(), ARM_ANGLE_INTEGER_DIGITS, ARM_ANGLE_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_ARM_ANGLE)
                .validateRange(dto.getArmAngle(), ARM_ANGLE_MIN_VALUE, ARM_ANGLE_MAX_VALUE,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_ARM_ANGLE);
    }

    @Override
    protected void validateParentReferences(CantileverDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getProfileId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_ID);
    }

    @Override
    protected void validateNestedDtos(CantileverDTO dto, List<Alert> alerts) {
        validateChild(alerts, dto.getSteadyArm(), steadyArmValidator, FIELD_STEADY_ARM);
    }
}
