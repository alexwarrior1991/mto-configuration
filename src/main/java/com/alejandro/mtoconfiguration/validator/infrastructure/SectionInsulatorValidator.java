package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;

@Component
public class SectionInsulatorValidator extends NormalEntityValidator<SectionInsulatorDTO> {

    private static final String ENTITY_NAME = "sectionInsulator";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ENABLED = "enabled";
    private static final String FIELD_STATION_ID = "stationId";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(SectionInsulatorDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getEnabled(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ENABLED)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);
    }

    @Override
    protected void validateParentReferences(SectionInsulatorDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getStationId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STATION_ID);
    }
}
