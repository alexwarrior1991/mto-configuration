package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SWITCH_CODE_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SWITCH_CODE_MIN_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SWITCH_CODE_PATTERN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TURNOUT_DENOMINATOR_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TURNOUT_DENOMINATOR_MIN;

@Component
public class SectionInsulatorSwitchValidator extends NormalEntityValidator<SectionInsulatorSwitchDTO> {

    private static final String ENTITY_NAME = "sectionInsulatorSwitch";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_TURNOUT_DENOMINATOR = "turnoutDenominator";
    private static final String FIELD_ENABLED = "enabled";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(SectionInsulatorSwitchDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getCode(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CODE)
                .validateRequiredField(dto.getEnabled(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ENABLED)
                .validateLengthField(dto.getCode(), SWITCH_CODE_MIN_LENGTH, SWITCH_CODE_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CODE)
                .validateFormat(dto.getCode(), SWITCH_CODE_PATTERN,
                        ErrorCodes.VALIDATION_INVALID_FORMAT, FIELD_CODE)
                // El KP y la tangente son opcionales: el plano no los rotula en todas las agujas.
                // Cuando vienen, sí tienen que caber en su columna, porque aceptar aquí más de lo
                // que admite la columna sólo cambia el 400 con el campo señalado por un 500 del
                // driver.
                .validateBigDecimalWithPrecision(dto.getKp(), KP_INTEGER_DIGITS, KP_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP)
                .validateRange(dto.getKp(), BigDecimal.ZERO, null,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP)
                .validateRangeIfTrueCondition(dto.getTurnoutDenominator() != null,
                        dto.getTurnoutDenominator(), TURNOUT_DENOMINATOR_MIN, TURNOUT_DENOMINATOR_MAX,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_TURNOUT_DENOMINATOR);
    }

    /**
     * La aguja no exige padre: en un alta anidada llega dentro del aislador y el id del padre
     * todavía no existe. La vía es opcional porque no siempre se conoce.
     */
    @Override
    protected void validateParentReferences(SectionInsulatorSwitchDTO dto, List<Alert> alerts) {
        // Sin referencias obligatorias.
    }
}
