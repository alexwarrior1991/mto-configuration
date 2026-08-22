package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;

@Component
@RequiredArgsConstructor
public class StationValidator extends NormalEntityValidator<StationDTO> {

    private static final String ENTITY_NAME = "station";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_EXECUTION_PACKAGE_ID = "executionPackageId";
    private static final String FIELD_TRACKS = "tracks";
    private static final String FIELD_DISCONNECTORS = "disconnectors";
    private static final String FIELD_SECTION_INSULATORS = "sectionInsulators";

    private final TrackValidator trackValidator;
    private final DisconnectorValidator disconnectorValidator;
    private final SectionInsulatorValidator sectionInsulatorValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(StationDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);
    }

    @Override
    protected void validateParentReferences(StationDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getExecutionPackageId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_EXECUTION_PACKAGE_ID);
    }

    /**
     * Las tres colecciones se persisten en cascada desde {@code Station}, así que se validan aquí.
     * Cada validador se ocupa solo de sus hijos directos: si el mismo hijo llega por dos ramas del
     * payload, se señalan las dos rutas, que es exactamente lo que el cliente necesita corregir.
     */
    @Override
    protected void validateNestedDtos(StationDTO dto, List<Alert> alerts) {
        validateChildren(alerts, dto.getTracks(), trackValidator, FIELD_TRACKS);
        validateChildren(alerts, dto.getDisconnectors(), disconnectorValidator, FIELD_DISCONNECTORS);
        validateChildren(alerts, dto.getSectionInsulators(), sectionInsulatorValidator, FIELD_SECTION_INSULATORS);
    }
}
