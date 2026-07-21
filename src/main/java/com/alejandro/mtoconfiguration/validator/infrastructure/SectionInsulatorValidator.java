package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
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
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getEnabled(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ENABLED)
                .validateRequiredFieldIfTrueCondition(
                        Utils.exists(dto),
                        dto.getStationId(),
                        ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        FIELD_STATION_ID
                )
                .validateLengthField(dto.getName(), 1, 100, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);
    }
}
