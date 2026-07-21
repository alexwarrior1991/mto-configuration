package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
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
public class DisconnectorValidator extends NormalEntityValidator<DisconnectorDTO> {


    private static final String ENTITY_NAME = "disconnector";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ON_LOAD = "onLoad";
    private static final String FIELD_STATION_ID = "stationId";
    private static final String FIELD_PROFILE_ID = "profileId";
    private static final String FIELD_DISCONNECTOR_FUNCTION = "disconnectorFunction";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(DisconnectorDTO dto, List<Alert> alerts) {
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getOnLoad(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ON_LOAD)
                .validateRequiredField(dto.getStationId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STATION_ID)
                .validateRequiredField(dto.getProfileId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_ID)
                .validateRequiredLovDTO(dto.getDisconnectorFunction(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_DISCONNECTOR_FUNCTION)
                .validateLengthField(dto.getName(), 1, 100, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);
    }
}
