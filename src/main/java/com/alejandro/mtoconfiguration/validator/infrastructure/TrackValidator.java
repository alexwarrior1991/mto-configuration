package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;

@Component
@RequiredArgsConstructor
public class TrackValidator extends NormalEntityValidator<TrackDTO> {

    private static final String ENTITY_NAME = "track";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ENABLED = "enabled";
    private static final String FIELD_EXECUTION_PACKAGE_ID = "executionPackageId";
    private static final String FIELD_PROFILES = "profiles";

    private final ProfileValidator profileValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(TrackDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getEnabled(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ENABLED)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);
    }

    /**
     * {@code stationId} no se exige: la columna {@code STATION_ID} de {@code TRACK} es anulable a
     * propósito, así que una vía puede colgar directamente del paquete de ejecución.
     */
    @Override
    protected void validateParentReferences(TrackDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getExecutionPackageId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_EXECUTION_PACKAGE_ID);
    }

    @Override
    protected void validateNestedDtos(TrackDTO dto, List<Alert> alerts) {
        validateChildren(alerts, dto.getProfiles(), profileValidator, FIELD_PROFILES);
    }
}
