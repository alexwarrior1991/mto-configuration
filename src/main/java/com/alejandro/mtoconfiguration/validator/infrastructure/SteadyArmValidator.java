package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.STEADY_ARM_LENGTH_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.STEADY_ARM_LENGTH_MIN;

@Component
public class SteadyArmValidator extends NormalEntityValidator<SteadyArmDTO> {

    private static final String ENTITY_NAME = "steadyArm";
    private static final String FIELD_LENGTH = "length";
    private static final String FIELD_STEADY_ARM_TYPE = "steadyArmType";
    private static final String FIELD_CANTILEVER_ID = "cantileverId";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    /**
     * El tipo de brazo es obligatorio; la longitud no.
     *
     * <p>El origen trae 5.691 mensulas con el tipo y sin la longitud: no se conoce.
     * Exigirla obligaba a elegir entre tirar el tipo o inventarse un numero, asi que la
     * columna pasa a admitir nulo (V12) y aqui solo se comprueba el rango cuando el
     * valor viene. Lo que no se relaja es ese rango: 0 o 2001 siguen siendo un 400 con
     * el campo señalado, no un 500 del driver.
     */
    @Override
    protected void validateRequiredFields(SteadyArmDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredLovDTO(dto.getSteadyArmType(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STEADY_ARM_TYPE)
                .validateRange(dto.getLength(), STEADY_ARM_LENGTH_MIN, STEADY_ARM_LENGTH_MAX,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_LENGTH);
    }

    @Override
    protected void validateParentReferences(SteadyArmDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getCantileverId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_CANTILEVER_ID);
    }
}
