package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.utils.ValidatorUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class NormalEntityValidator<T extends BaseDTO> extends CRUDValidator<T> {

    private static final String FIELD_ID = "id";

    protected abstract String getEntityName();

    protected abstract void validateRequiredFields(T dto, List<Alert> alerts);

    @Override
    public List<Alert> validateBeforeSave(T dto) {
        List<Alert> alerts = new ArrayList<>();

        if (dto == null){
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, getEntityName()));
            return alerts;
        }

        validateRequiredFields(dto, alerts);
        return alerts;
    }

    @Override
    public List<Alert> validateBeforeUpdate(T dto) {
        List<Alert> alerts = new ArrayList<>();

        if (dto == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, getEntityName()));
            return alerts;
        }

        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ID);

        alerts.addAll(validateBeforeSave(dto));
        return alerts;
    }

    @Override
    public List<Alert> validateBeforeDelete(T dto) {
        List<Alert> alerts = new ArrayList<>();

        if (dto == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, getEntityName()));
            return alerts;
        }

        new ValidatorUtils(alerts)
                .validateRequiredField(dto.getId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ID);

        return alerts;
    }

    @Override
    protected void loadRequireFields() {
        // No se usa porque validamos con ValidatorUtils.
    }


}
