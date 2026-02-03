package com.alejandro.mtoconfiguration.utils;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.validator.commons.Validator;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Setter
@Getter
public class ValidatorUtils {

    private List<Alert> alerts;

    public ValidatorUtils(List<Alert> alerts) {
        this.alerts = alerts;
    }

    public ValidatorUtils of(List<Alert> alerts) {
        return new ValidatorUtils(alerts);
    }

    public static boolean different(Date date1, Date date2) {
        return date1 == null && date2 != null || date1 != null && date2 == null || date1 != null && !date1.equals(date2);
    }

    public ValidatorUtils validateDate1IsAfterDate2(LocalDate date1, LocalDate date2, String dateErrorMessage, String fieldName) {

        if (date1 != null && date2 != null && date1.isBefore(date2)) {
            getAlerts().add(Alert.ofDanger(dateErrorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateDate1IsAfterDate2(Date date1, Date date2, String dateErrorMessage, String timeErrorMessage, String fieldName) {

        if (date1 != null && date2 != null) {
            LocalDate d1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate d2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            OffsetDateTime offsetDateTime1 = date1.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            OffsetDateTime offsetDateTime2 = date2.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();

            if (d1.isBefore(d2)) {
                getAlerts().add(Alert.ofDanger(dateErrorMessage, fieldName));
            } else if (offsetDateTime1.isBefore(offsetDateTime2)) {
                getAlerts().add(Alert.ofDanger(timeErrorMessage, fieldName));
            }
        }

        return this;
    }

    public ValidatorUtils validateDateIsAfterNow(Date date, String dateErrorMessage, String timeErrorMessage, String fieldName) {
        if (date == null) {
            return this;
        }

        LocalDate d1 = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate d2 = LocalDate.now();

        OffsetDateTime offsetDateTime1 = date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime offsetDateTime2 = OffsetDateTime.now();

        if (d1.isBefore(d2)) {
            getAlerts().add(Alert.ofDanger(dateErrorMessage, fieldName));
        } else if (offsetDateTime1.isBefore(offsetDateTime2)) {
            getAlerts().add(Alert.ofDanger(timeErrorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateDateIsBeforeNow(Date date, String dateErrorMessage, String timeErrorMessage, String fieldName) {
        if (date == null) {
            return this;
        }

        LocalDate d1 = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate d2 = LocalDate.now();

        OffsetDateTime offsetDateTime1 = date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime offsetDateTime2 = OffsetDateTime.now();

        if (d1.isAfter(d2)) {
            getAlerts().add(Alert.ofDanger(dateErrorMessage, fieldName));
        } else if (offsetDateTime1.isAfter(offsetDateTime2)) {
            getAlerts().add(Alert.ofDanger(timeErrorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateRequiredField(Object field, String errorMessage, String fieldName) {
        if (field == null) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateRequiredFieldIfTrueCondition(boolean condition, Object field, String errorMessage, String fieldName) {
        if (condition) {
            return validateRequiredField(field, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredDTO(BaseDTO dto, String errorMessage, String fieldName) {
        if (Utils.notExists(dto)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateRequiredDTOIfTrueCondition(boolean condition, BaseDTO dto, String errorMessage, String fieldName) {
        if (condition) {
            return validateRequiredDTO(dto, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredLovCode(LovDTO dto, String errorMessage, String fieldName) {
        if (dto == null || dto.getCode() == null) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateRequiredLovCodeIfTrueCondition(boolean condition, LovDTO dto, String errorMessage, String fieldName) {
        if (condition) {
            return validateRequiredLovCode(dto, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateLengthField(Object field, int min, int max, String errorMessage, String fieldName) {
        String value = switch (field) {
            case Integer i -> i.toString();
            case Long l -> l.toString();
            case String s -> s;
            case null, default -> null;
        };

        if (value != null && (value.length() < min || value.length() > max)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max)));
        }

        return this;
    }

    public ValidatorUtils validateLengthFieldIfTrueCondition(boolean condition, Object field, int min, int max, String errorMessage, String fieldName) {
        if (condition) {
            return validateLengthField(field, min, max, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateLengthLovCodeIfTrueCondition(boolean condition, LovDTO field, int min, int max, String errorMessage, String fieldName) {
        if (condition && field != null && field.getCode() != null) {
            return validateLengthField(field.getCode(), min, max, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateFormat(String field, String regex, String errorMessage, String fieldName) {
        if (field == null || regex == null) {
            return this;
        }

        if (!field.matches(regex)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateFormatIfTrueCondition(boolean condition, String field, String regex, String errorMessage, String fieldName) {
        if (condition) {
            return validateFormat(field, regex, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateContainerControlDigit(String field, String errorMessage, String fieldName) {
        return validateContainerControlDigit(true, field, errorMessage, fieldName);
    }

    public ValidatorUtils validateContainerControlDigit(boolean condition, String field, String errorMessage, String fieldName) {
        if (condition) {
            Map<Character, Integer> letterCodes = new HashMap<>();

            char character = 'A';
            int code = 10;

            while (true) {
                if (code == 11 || code == 22 || code == 33) {
                    code++;
                }

                letterCodes.put(character, code);

                if (character == 'Z') {
                    break;
                }

                character++;
                code++;
            }

            int sum = 0;
            for (int i = 0; i < 10; i++) {
                if (i < 4) {
                    sum += (int) (letterCodes.get(field.charAt(i)) * Math.pow(2, i));
                } else {
                    sum += (int) (Integer.parseInt("" + field.charAt(i)) * Math.pow(2, i));
                }
            }

            int controlDigit = sum - (((int) Math.floor(sum / 11.0)) * 11);

            if (controlDigit != Integer.parseInt("" + field.charAt(10))) {
                getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
            }
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalWithSplit(BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        if (value == null) {
            return this;
        }

        String[] parts = value.toPlainString().split("\\.");
        String integerPart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "";

        if (integerPart.length() > maxIntegers || decimalPart.length() > maxDecimals) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalWithSplitIfTrueCondition(boolean condition, BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        if (condition) {
            return validateBigDecimalWithSplit(value, maxIntegers, maxDecimals, errorMessage, fieldName);
        }
        return this;
    }

    public ValidatorUtils validateBigDecimalWithPrecision(BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        if (value == null) {
            return this;
        }

        int precision = value.precision();
        int scale = value.scale();
        int integerDigits = precision - scale;

        if (integerDigits > maxIntegers || scale > maxDecimals) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalWithPrecisionIfTrueCondition(boolean condition, BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        if (condition) {
            return validateBigDecimalWithPrecision(value, maxIntegers, maxDecimals, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRange(Integer value, int min, int max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, "" + min, "" + max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, Integer value, int min, int max, String errorMessage, String fieldName) {
        if (condition) {
            return validateRange(value, min, max, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRange(Double value, double min, double max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, "" + min, "" + max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, Double value, double min, double max, String errorMessage, String fieldName) {
        if (condition) {
            return validateRange(value, min, max, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRange(BigDecimal value, BigDecimal min, BigDecimal max, String errorMessage, String fieldName) {
        if (value != null && (value.compareTo(min) < 0 || value.compareTo(max) > 0)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName, "" + min, "" + max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, BigDecimal value, BigDecimal min, BigDecimal max, String errorMessage, String fieldName) {
        if (condition) {
            return validateRange(value, min, max, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateNumericField(Object field, String errorMessage, String fieldName) {
        if (field instanceof Number) {
            return this;
        }

        if (field instanceof String && !((String) field).matches("-?\\d+(\\.\\d+)?")) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateNumericFieldIfTrueCondition(boolean condition, Object field, String errorMessage, String fieldName) {
        if (condition) {
            return validateNumericField(field, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateDateIsBeforeNow(LocalDateTime date, String errorMessage, String fieldName) {
        if (date != null && date.isAfter(LocalDateTime.now())) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateDateIsAfterNow(LocalDateTime date, String errorMessage, String fieldName) {
        if (date != null && date.isBefore(LocalDateTime.now())) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateLocalDate1IsBeforeDate2(LocalDateTime date1, LocalDateTime date2, String errorMessage, String fieldName) {
        if (date1 != null && date2 != null && date1.isAfter(date2)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateLocalDate1IsAfterDate2(LocalDateTime date1, LocalDateTime date2, String errorMessage, String fieldName) {
        if (date1 != null && date2 != null && date1.isBefore(date2)) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateLocalDateTime1IsAfterDateTime2(LocalDateTime date1, LocalDateTime date2,
                                                                 String dateErrorMessage, String timeErrorMessage, String fieldName) {

        if (date1 != null && date2 != null) {
            LocalDate d1 = date1.toLocalDate();
            LocalDate d2 = date2.toLocalDate();

            if (d1.isBefore(d2)) {
                getAlerts().add(Alert.ofDanger(dateErrorMessage, fieldName));
            } else if (date1.isBefore(date2)) {
                getAlerts().add(Alert.ofDanger(timeErrorMessage, fieldName));
            }
        }

        return this;
    }

    public boolean different(LocalDateTime date1, LocalDateTime date2) {
        return date1 == null && date2 != null || date1 != null && date2 == null || date1 != null && !date1.equals(date2);
    }

    public ValidatorUtils validateRequiredLovDTO(LovDTO dto, String errorMessage, String fieldName) {
        if (dto == null || (dto.getId() == null && StringUtils.isBlank(dto.getCode()))) {
            getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }

    public ValidatorUtils validateRequiredLovDTOIfTrueCondition(boolean condition, LovDTO dto, String errorMessage,
                                                                String fieldName) {
        if (condition) {
            return validateRequiredLovDTO(dto, errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalEqualityIfTrueCondition(boolean condition, BigDecimal value1, BigDecimal value2, String errorMessage, String fieldName) {
        return condition ? this.validateBigDecimalEquality(value1, value2, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateBigDecimalEquality(BigDecimal value1, BigDecimal value2, String errorMessage, String fieldName) {
        if (value1 == null || value2 == null || value1.compareTo(value2) != 0) {
            this.getAlerts().add(Alert.ofDanger(errorMessage, fieldName));
        }

        return this;
    }


    public static boolean notExists(BaseDTO dto) {
        return !exists(dto);
    }

    public static boolean exists(BaseDTO dto) {
        return dto != null && dto.getId() != null;
    }


}
