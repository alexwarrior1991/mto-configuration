package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.utils.ValidatorUtils;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.*;

@Component
@RequestScope
@Slf4j
public class ProfileValidator extends CRUDValidator<ProfileDTO> {


    private static final String FIELD_ID = "id";
    private static final String FIELD_PROFILE_ID = "profileId";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_TRACK_ID = "trackId";
    private static final String FIELD_PROFILE_STATUS = "profileStatus";

    @Override
    public List<Alert> validateBeforeSave(ProfileDTO dto) {
        List<Alert> alerts = new ArrayList<>();

        if (dto == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "profile"));
            return alerts;
        }

        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getProfileId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_ID)
                .validateRequiredField(dto.getKp(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_KP)
                .validateRequiredField(dto.getTrackId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_TRACK_ID)
                .validateRequiredLovDTO(dto.getProfileStatus(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_STATUS)
                .validateLengthField(dto.getProfileId(), 1, 50, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_PROFILE_ID)
                .validateLengthField(dto.getKp(), 1, 50, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP);

        return alerts;
    }

    @Override
    public List<Alert> validateBeforeUpdate(ProfileDTO dto) {
        List<Alert> alerts = new ArrayList<>();

        if (dto == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "profile"));
            return alerts;
        }

        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ID);

        alerts.addAll(validateBeforeSave(dto));

        return alerts;
    }

    @Override
    public List<Alert> validateBeforeBulkSave(List<ProfileDTO> dtoList) {
        List<Alert> alerts = new ArrayList<>();

        if (CollectionUtils.isEmpty(dtoList)) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles"));
            return alerts;
        }

        validateItemsBeforeSave(dtoList, alerts);
        validateDuplicatedProfileIds(dtoList, alerts);

        return alerts;
    }

    @Override
    public List<Alert> validateBeforeBulkUpdate(List<ProfileDTO> dtoList) {
        List<Alert> alerts = new ArrayList<>();

        if (CollectionUtils.isEmpty(dtoList)) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles"));
            return alerts;
        }

        validateItemsBeforeUpdate(dtoList, alerts);
        validateDuplicatedIds(dtoList, alerts);
        validateDuplicatedProfileIds(dtoList, alerts);

        return alerts;
    }

    @Override
    public List<Alert> validateBeforeDelete(ProfileDTO dto) {
        List<Alert> alerts = new ArrayList<>();

        if (dto == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, "profile"));
            return alerts;
        }

        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ID);

        return alerts;
    }

    @Override
    protected void loadRequireFields() {
        // No lo usaría para este caso si vas a validar con ValidatorUtils.
        // Puedes dejarlo vacío.
    }

    private void validateItemsBeforeSave(List<ProfileDTO> dtoList, List<Alert> alerts) {
        for (int i = 0; i < dtoList.size(); i++) {
            List<Alert> itemAlerts = validateBeforeSave(dtoList.get(i));
            addIndexedAlerts(alerts, itemAlerts, i);
        }
    }

    private void validateItemsBeforeUpdate(List<ProfileDTO> dtoList, List<Alert> alerts) {
        for (int i = 0; i < dtoList.size(); i++) {
            List<Alert> itemAlerts = validateBeforeUpdate(dtoList.get(i));
            addIndexedAlerts(alerts, itemAlerts, i);
        }
    }

    private void validateDuplicatedIds(List<ProfileDTO> dtoList, List<Alert> alerts) {
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < dtoList.size(); i++) {
            ProfileDTO dto = dtoList.get(i);

            if (dto == null || dto.getId() == null) {
                continue;
            }

            if (!ids.add(dto.getId())) {
                alerts.add(Alert.ofDanger(
                        ErrorCodes.DUPLICATED_RESOURCE,
                        "profiles[" + i + "].id"
                ));
            }
        }
    }

    private void validateDuplicatedProfileIds(List<ProfileDTO> dtoList, List<Alert> alerts) {
        Set<String> profileIds = new HashSet<>();

        for (int i = 0; i < dtoList.size(); i++) {
            ProfileDTO dto = dtoList.get(i);

            if (dto == null || dto.getProfileId() == null || dto.getProfileId().isBlank()) {
                continue;
            }

            String normalizedProfileId = dto.getProfileId().trim().toUpperCase();

            if (!profileIds.add(normalizedProfileId)) {
                alerts.add(Alert.ofDanger(
                        ErrorCodes.DUPLICATED_RESOURCE,
                        "profiles[" + i + "].profileId"
                ));
            }
        }
    }

    private void addIndexedAlerts(List<Alert> alerts, List<Alert> itemAlerts, int index) {
        if (CollectionUtils.isEmpty(itemAlerts)) {
            return;
        }

        itemAlerts.stream()
                .filter(Objects::nonNull)
                .forEach(alert -> alerts.add(alert));
    }
}
