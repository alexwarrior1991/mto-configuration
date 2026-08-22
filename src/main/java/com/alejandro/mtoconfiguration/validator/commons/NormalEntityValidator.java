package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador estándar de una entidad CRUD.
 *
 * <p>Divide la validación en tres bloques para que el contexto (raíz o anidado) sea explícito y no
 * se tenga que deducir de si el DTO trae id o no:</p>
 *
 * <ul>
 *   <li>{@link #validateRequiredFields} — campos propios; se exigen siempre.</li>
 *   <li>{@link #validateParentReferences} — claves ajenas al padre; solo se exigen cuando el DTO
 *       se crea o modifica de forma independiente, porque al viajar anidado las rellena el mapper
 *       a partir del padre.</li>
 *   <li>{@link #validateNestedDtos} — hijos del DTO; se validan en alta y en modificación.</li>
 * </ul>
 */
public abstract class NormalEntityValidator<T extends BaseDTO> extends CRUDValidator<T> {

    protected static final String FIELD_ID = "id";

    protected abstract void validateRequiredFields(T dto, List<Alert> alerts);

    protected void validateParentReferences(T dto, List<Alert> alerts) {
        // Por defecto la entidad no depende de ningún padre.
    }

    protected void validateNestedDtos(T dto, List<Alert> alerts) {
        // Por defecto la entidad no tiene hijos que validar.
    }

    @Override
    public List<Alert> validateBeforeSave(T dto) {
        return validateNew(dto, true);
    }

    @Override
    public List<Alert> validateBeforeSaveAsChild(T dto) {
        return validateNew(dto, false);
    }

    @Override
    public List<Alert> validateBeforeUpdate(T dto) {
        return validateExisting(dto, true);
    }

    @Override
    public List<Alert> validateBeforeUpdateAsChild(T dto) {
        return validateExisting(dto, false);
    }

    @Override
    public List<Alert> validateBeforeDelete(T dto) {
        List<Alert> alerts = new ArrayList<>();

        if (addMissingDtoAlert(dto, alerts)) {
            return alerts;
        }

        check(alerts).validateRequiredField(dto.getId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ID);

        return alerts;
    }

    private List<Alert> validateNew(T dto, boolean root) {
        List<Alert> alerts = new ArrayList<>();

        if (addMissingDtoAlert(dto, alerts)) {
            return alerts;
        }

        validateRequiredFields(dto, alerts);

        if (root) {
            validateParentReferences(dto, alerts);
        }

        validateNestedDtos(dto, alerts);

        return alerts;
    }

    private List<Alert> validateExisting(T dto, boolean root) {
        List<Alert> alerts = new ArrayList<>();

        if (addMissingDtoAlert(dto, alerts)) {
            return alerts;
        }

        check(alerts).validateRequiredField(dto.getId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ID);
        alerts.addAll(validateNew(dto, root));

        return alerts;
    }

    private boolean addMissingDtoAlert(T dto, List<Alert> alerts) {
        if (dto == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, getEntityName()));
            return true;
        }

        return false;
    }
}
