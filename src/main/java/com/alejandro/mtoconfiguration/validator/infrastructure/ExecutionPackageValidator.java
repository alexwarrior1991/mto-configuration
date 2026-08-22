package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;

@Component
@RequiredArgsConstructor
public class ExecutionPackageValidator extends NormalEntityValidator<ExecutionPackageDTO> {

    private static final String ENTITY_NAME = "executionPackage";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_INITIAL_PACKAGE = "initialPackage";
    private static final String FIELD_LENGTH = "length";
    private static final String FIELD_START_DATE = "startDate";
    private static final String FIELD_END_DATE = "endDate";
    private static final String FIELD_COMPANY_ID = "companyId";
    private static final String FIELD_TRACKS = "tracks";
    private static final String FIELD_STATIONS = "stations";

    private final TrackValidator trackValidator;
    private final StationValidator stationValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(ExecutionPackageDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getInitialPackage(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_INITIAL_PACKAGE)
                .validateRequiredField(dto.getLength(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_LENGTH)
                .validateRequiredField(dto.getStartDate(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_START_DATE)
                .validateRequiredField(dto.getEndDate(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_END_DATE)
                .validateRequiredField(dto.getCompanyId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_COMPANY_ID)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME)
                .validateDate1IsAfterDate2(dto.getEndDate(), dto.getStartDate(),
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_END_DATE);
    }

    /**
     * Los hijos se validan una sola vez, tanto en alta como en modificación, y cada uno según su
     * propio estado: uno sin id se valida como alta aunque el paquete se esté actualizando, de modo
     * que se pueda añadir una vía o una estación nuevas dentro de un update.
     */
    @Override
    protected void validateNestedDtos(ExecutionPackageDTO dto, List<Alert> alerts) {
        validateChildren(alerts, dto.getTracks(), trackValidator, FIELD_TRACKS);
        validateChildren(alerts, dto.getStations(), stationValidator, FIELD_STATIONS);
    }
}
