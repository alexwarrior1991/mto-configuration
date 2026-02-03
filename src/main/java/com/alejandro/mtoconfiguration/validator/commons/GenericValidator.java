package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class GenericValidator<T extends BaseDTO> implements Validator<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Alert> alerts;
    private List<String> requiredFields;

    protected abstract void loadRequireFields();

    protected String getRequiredMessage() {
        return ErrorCodes.VALIDATION_REQUIRED_FIELD;
    }

    public List<String> getRequiredFields() {
        return requiredFields != null ? requiredFields : new ArrayList<>();
    }

    public Alert requiredFieldsToAlert(T dto, List<String> fieldsToValidate) {
        if (CollectionUtils.isEmpty(fieldsToValidate)) {
            return null;
        }

        String[] invalidFields = fieldsToValidate.stream()
                .filter(field -> validateField(field, dto))
                .toArray(String[]::new);

        return invalidFields.length == 0
                ? null
                : Alert.ofDanger(getRequiredMessage(), invalidFields);
    }

    private boolean validateField(String fieldToValidate, T dto) {
        final String capitalized = StringUtils.capitalize(fieldToValidate);
        final String[] methodNames = {"get" + capitalized, "is" + capitalized};

        // Filters methods by name and parameter count
        return Arrays.stream(dto.getClass().getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method ->
                        Arrays.stream(methodNames)
                                .anyMatch(name -> name.equals(method.getName()))
                )
                .findFirst()
                .map(method -> {
                    // Invokes getter; handles access or invocation exceptions
                    try {
                        Object value = method.invoke(dto);
                        return validateObjectField(value);
                    } catch (IllegalAccessException e) {
                        log.warn("The field '{}' to validate required is not accessible in class {}",
                                fieldToValidate, dto.getClass().getName(), e);
                    } catch (InvocationTargetException e) {
                        log.warn("The field '{}' to validate required threw an exception in class {}",
                                fieldToValidate, dto.getClass().getName(), e);
                    }

                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Field '{}' to validate required doesn't have a get/is method in class {}",
                            fieldToValidate, dto.getClass().getName());
                    return true;
                });
    }

    private boolean validateObjectField(Object object) {
        if (object instanceof String string) {
            return StringUtils.isBlank(string);
        } else {
            return true;
        }
    }

    public List<Alert> getAlerts() {
        return alerts != null ? alerts : new ArrayList<>();
    }

    public void processIndexErrors(List<Alert> errors, int index, String... tableName) {
        processIndexErrors(errors, null, index, tableName);
    }

    public void processIndexErrors(List<Alert> errors, Integer parentIndex, int index, String... tableName) {
        if (CollectionUtils.isEmpty(errors)) {
            return;
        }

        String prefix = (tableName != null && tableName.length > 0)
                ? tableName[0] + "_"
                : "";

        // Updates alert fields with index and table prefix
        errors.forEach(error -> {
            List<String> newFields = error.getFields().stream()
                    .map(field -> {
                        String indexedField = (parentIndex != null)
                                ? field + "_" + parentIndex + "_" + index
                                : field + "_" + index;
                        return prefix + indexedField;
                    })
                    .toList();

            error.setFields(newFields);
        });

        this.alerts.addAll(errors);
    }

    public void validateIndex(T baseDTO, int index, String... tableName) {
        validateIndex(baseDTO, null, index, tableName);
    }

    public void validateIndex(T baseDTO, Integer parentIndex, int index, String... tableName) {
        try {
            final Validator<T> validator = getNewValidatorInstance();

            if (validator != null) {
                final List<Alert> errors = validator.validateBeforeSave(baseDTO);
                processIndexErrors(errors, parentIndex, index, tableName);
            }
        } catch (Exception e) {
            final Alert alert = Alert.ofDanger("generic.failed").addStacktrace(e);
            getAlerts().add(alert);
            log.error("Error validating invoice item: {}", e.getMessage(), e);
        }
    }

    public void validateRequiredField(Object field, String errorMessage, String fieldName) {
        if (field == null) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }
    }

    public void validateLengthField(Object field, int min, int max, String errorMessage, String fieldName) {
        if (field == null) {
            return;
        }

        String value = switch (field) {
            case String s -> s;
            case Integer i -> i.toString();
            case Long l -> l.toString();
            default -> null;
        };

        if (value != null && (value.length() < min || value.length() > max)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max)));
        }
    }

    public void validateRange(Integer value, int min, int max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max)));
        }
    }

    public void validateRange(Double value, double min, double max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max)));
        }
    }

    public void validateNumericField(Object field, String errorMessage, String fieldName) {
        if (field instanceof Number) {
            return;
        }

        if (field instanceof String stringValue && !StringUtils.isNumeric(stringValue)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }
    }

    public void validateDateIsBeforeNow(LocalDateTime date, String errorMessage, String fieldName) {
        if (date != null && date.isAfter(LocalDateTime.now())) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }
    }

    public void validateDateIsBeforeNow(Date date, String errorMessage, String fieldName) {
        if (date == null) {
            return;
        }

        OffsetDateTime d1 = date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();

        if (d1.isAfter(OffsetDateTime.now())) {
            this.getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }
    }

    public void validateDateIsAfterNow(LocalDateTime date, String errorMessage, String fieldName) {
        if (date != null && date.isBefore(LocalDateTime.now())) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }
    }

    public void validateDateIsAfterNow(Date date, String errorMessage, String fieldName) {
        if (date == null) {
            return;
        }

        OffsetDateTime d1 = date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();

        if (d1.isBefore(OffsetDateTime.now())) {
            this.getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }
    }

    public void validateDate1IsBeforeDate2(LocalDateTime date1, LocalDateTime date2, String errorMessage) {
        if (date1 != null && date2 != null && date1.isAfter(date2)) {
            getAlerts().add(Alert.ofDanger(errorMessage));
        }
    }

    public void validateDate1IsAfterDate2(LocalDateTime date1, LocalDateTime date2, String errorMessage) {
        if (date1 != null && date2 != null && date1.isBefore(date2)) {
            getAlerts().add(Alert.ofDanger(errorMessage));
        }
    }

    public void validateDateTime1IsAfterDateTime2(LocalDateTime date1, LocalDateTime date2,
                                                  String dateErrorMessage, String timeErrorMessage) {

        if (date1 != null && date2 != null) {
            LocalDate d1 = date1.toLocalDate();
            LocalDate d2 = date2.toLocalDate();

            if (d1.isBefore(d2)) {
                getAlerts().add(Alert.ofDanger(dateErrorMessage));
            } else if (date1.isBefore(date2)) {
                getAlerts().add(Alert.ofDanger(timeErrorMessage));
            }
        }
    }

    public void validateDate1IsAfterDate2(Date date1, Date date2,
                                          String dateErrorMessage, String timeErrorMessage) {

        if (date1 != null && date2 != null) {
            LocalDate d1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate d2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            OffsetDateTime offsetDateTime1 = date1.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            OffsetDateTime offsetDateTime2 = date2.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();

            if (d1.isBefore(d2)) {
                getAlerts().add(Alert.ofDanger(dateErrorMessage));
            } else if (offsetDateTime1.isBefore(offsetDateTime2)) {
                getAlerts().add(Alert.ofDanger(timeErrorMessage));
            }
        }
    }

    public boolean different( LocalDateTime date1, LocalDateTime date2 ) {
        return date1 == null && date2 != null || date1 != null && date2 == null || date1 != null && !date1.equals( date2 );
    }

    public static boolean different( Date date1, Date date2) {
        return date1 == null && date2 != null || date1 != null && date2 == null || date1 != null && !date1.equals(date2);
    }


    @SuppressWarnings("unchecked")
    private Validator<T> getNewValidatorInstance() throws InstantiationException, IllegalAccessException {
        try {
            return (Validator<T>) Class.forName(getValidatorClassName()).newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private String getEntityName() {
        final String serviceName = this.getClass().getSimpleName();

        return serviceName.replace("Service", "");
    }

    private String getValidatorClassName() {
        return this.getClass().getPackage().getName() + "." + getEntityName() + "Validator";
    }
}
