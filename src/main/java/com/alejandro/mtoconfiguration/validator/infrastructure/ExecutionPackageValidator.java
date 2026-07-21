package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
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
import java.util.function.Function;
import java.util.stream.IntStream;

@Component
@RequestScope
@Slf4j
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
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getInitialPackage(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_INITIAL_PACKAGE)
                .validateRequiredField(dto.getLength(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_LENGTH)
                .validateRequiredField(dto.getStartDate(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_START_DATE)
                .validateRequiredField(dto.getEndDate(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_END_DATE)
                .validateRequiredField(dto.getCompanyId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_COMPANY_ID)
                .validateLengthField(dto.getName(), 1, 100, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME);

        validateChildren(dto, alerts, false);
    }


    @Override
    public List<Alert> validateBeforeUpdate(ExecutionPackageDTO dto) {
        List<Alert> alerts = super.validateBeforeUpdate(dto);

        if (dto != null) {
            validateChildren(dto, alerts, true);
        }

        return alerts;
    }

    private void validateChildren(ExecutionPackageDTO dto, List<Alert> alerts, boolean update) {
        addIndexedAlerts(
                alerts,
                dto.getTracks(),
                update ? trackValidator::validateBeforeUpdate : trackValidator::validateBeforeSave,
                FIELD_TRACKS
        );

        addIndexedAlerts(
                alerts,
                dto.getStations(),
                update ? stationValidator::validateBeforeUpdate : stationValidator::validateBeforeSave,
                FIELD_STATIONS
        );
    }



    private <T> void addIndexedAlerts(List<Alert> alerts,
                                      List<T> items,
                                      Function<T, List<Alert>> validator,
                                      String fieldPrefix) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        IntStream.range(0, items.size())
                .mapToObj(index -> toIndexedAlerts(validator.apply(items.get(index)), fieldPrefix, index))
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .forEach(alerts::add);
    }

    private List<Alert> toIndexedAlerts(List<Alert> itemAlerts, String fieldPrefix, int index) {
        if (CollectionUtils.isEmpty(itemAlerts)) {
            return List.of();
        }

        itemAlerts.stream()
                .filter(Objects::nonNull)
                .forEach(alert -> alert.setFields(
                        alert.getFields().stream()
                                .map(field -> fieldPrefix + "[" + index + "]." + field)
                                .toList()
                ));

        return itemAlerts;
    }
}
