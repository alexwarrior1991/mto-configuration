package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import com.alejandro.mtoconfiguration.utils.ValidatorUtils;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

@Component
@RequestScope
@Slf4j
public class ProfileValidator extends NormalEntityValidator<ProfileDTO> {


    private static final String ENTITY_NAME = "profile";
    private static final String FIELD_PROFILE_ID = "profileId";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_TRACK_ID = "trackId";
    private static final String FIELD_PROFILE_STATUS = "profileStatus";
    private static final String FIELD_PROFILES = "profiles";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(ProfileDTO dto, List<Alert> alerts) {
        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getProfileId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_ID)
                .validateRequiredField(dto.getKp(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_KP)
                .validateRequiredFieldIfTrueCondition(
                        Utils.exists(dto),
                        dto.getTrackId(),
                        ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        FIELD_TRACK_ID
                )
                .validateRequiredLovDTO(dto.getProfileStatus(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_STATUS)
                .validateLengthField(dto.getProfileId(), 1, 50, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_PROFILE_ID)
                .validateLengthField(dto.getKp(), 1, 50, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP);
    }

    @Override
    public List<Alert> validateBeforeBulkSave(List<ProfileDTO> dtoList) {
        List<Alert> alerts = new ArrayList<>();

        if (CollectionUtils.isEmpty(dtoList)) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILES));
            return alerts;
        }

        validateItems(dtoList, alerts, this::validateBeforeSave);
        validateDuplicatedIds(dtoList, alerts);

        return alerts;
    }

    @Override
    public List<Alert> validateBeforeBulkUpdate(List<ProfileDTO> dtoList) {
        List<Alert> alerts = new ArrayList<>();

        if (CollectionUtils.isEmpty(dtoList)) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILES));
            return alerts;
        }

        validateItems(dtoList, alerts, this::validateBeforeUpdate);
        validateDuplicatedIds(dtoList, alerts);
        validateDuplicatedProfileIds(dtoList, alerts);

        return alerts;
    }

    private void validateItems(List<ProfileDTO> dtoList,
                               List<Alert> alerts,
                               Function<ProfileDTO, List<Alert>> validator) {
        IntStream.range(0, dtoList.size())
                .mapToObj(index -> toIndexedAlerts(validator.apply(dtoList.get(index)), index))
                .flatMap(list -> list.stream())
                .filter(Objects::nonNull)
                .forEach(alerts::add);
    }

    private List<Alert> toIndexedAlerts(List<Alert> itemAlerts, int index) {
        if (CollectionUtils.isEmpty(itemAlerts)) {
            return List.of();
        }

        itemAlerts.stream()
                .filter(Objects::nonNull)
                .forEach(alert -> alert.setFields(
                        alert.getFields().stream()
                                .map(field -> FIELD_PROFILES + "[" + index + "]." + field)
                                .toList()
                ));

        return itemAlerts;
    }

    private void validateDuplicatedIds(List<ProfileDTO> dtoList, List<Alert> alerts) {
        Set<Long> ids = new HashSet<>();

        IntStream.range(0, dtoList.size())
                .filter(index -> dtoList.get(index) != null)
                .filter(index -> dtoList.get(index).getId() != null)
                .filter(index -> !ids.add(dtoList.get(index).getId()))
                .forEach(index -> alerts.add(Alert.ofDanger(
                        ErrorCodes.DUPLICATED_RESOURCE,
                        FIELD_PROFILES + "[" + index + "].id"
                )));
    }

    private void validateDuplicatedProfileIds(List<ProfileDTO> dtoList, List<Alert> alerts) {
        Set<String> profileIds = new HashSet<>();

        IntStream.range(0, dtoList.size())
                .filter(index -> dtoList.get(index) != null)
                .filter(index -> dtoList.get(index).getProfileId() != null)
                .filter(index -> !dtoList.get(index).getProfileId().isBlank())
                .filter(index -> !profileIds.add(normalizeProfileId(dtoList.get(index).getProfileId())))
                .forEach(index -> alerts.add(Alert.ofDanger(
                        ErrorCodes.DUPLICATED_RESOURCE,
                        FIELD_PROFILES + "[" + index + "].profileId"
                )));
    }

    private String normalizeProfileId(String profileId) {
        return profileId.trim().toUpperCase();
    }


}
