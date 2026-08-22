package com.alejandro.mtoconfiguration.utils;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Caja de herramientas de validación. Acumula alertas sobre la lista recibida en construcción y
 * devuelve {@code this} para poder encadenar.
 *
 * <p>Salvo que se indique lo contrario, todos los métodos son <b>tolerantes a nulos</b>: un valor
 * nulo no genera alerta, porque la obligatoriedad se declara aparte con
 * {@link #validateRequiredField} o {@link #validateRequiredString}. Así una sola cadena puede
 * declarar «obligatorio» y «con este formato» sin duplicar el error cuando el campo falta.</p>
 */
@Getter
public class ValidatorUtils {

    /** Entero opcionalmente con signo y con parte decimal opcional. */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    /** Códigos ISO 6346 de las letras, saltando los múltiplos de 11 (11, 22, 33). */
    private static final Map<Character, Integer> CONTAINER_LETTER_CODES = buildContainerLetterCodes();

    private static final int CONTAINER_CODE_LENGTH = 11;

    private final List<Alert> alerts;

    public ValidatorUtils(List<Alert> alerts) {
        this.alerts = alerts;
    }

    public static ValidatorUtils of(List<Alert> alerts) {
        return new ValidatorUtils(alerts);
    }

    // --- Obligatoriedad ---

    public ValidatorUtils validateRequiredField(Object field, String errorMessage, String fieldName) {
        if (field == null) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredFieldIfTrueCondition(boolean condition, Object field, String errorMessage, String fieldName) {
        return condition ? validateRequiredField(field, errorMessage, fieldName) : this;
    }

    /**
     * Igual que {@link #validateRequiredField} pero rechazando también la cadena vacía o en blanco:
     * {@code "   "} no es un nombre válido aunque no sea nulo.
     */
    public ValidatorUtils validateRequiredString(String field, String errorMessage, String fieldName) {
        if (StringUtils.isBlank(field)) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredStringIfTrueCondition(boolean condition, String field, String errorMessage, String fieldName) {
        return condition ? validateRequiredString(field, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateRequiredDTO(BaseDTO dto, String errorMessage, String fieldName) {
        if (Utils.notExists(dto)) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredDTOIfTrueCondition(boolean condition, BaseDTO dto, String errorMessage, String fieldName) {
        return condition ? validateRequiredDTO(dto, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateRequiredLovCode(LovDTO dto, String errorMessage, String fieldName) {
        if (dto == null || StringUtils.isBlank(dto.getCode())) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredLovCodeIfTrueCondition(boolean condition, LovDTO dto, String errorMessage, String fieldName) {
        return condition ? validateRequiredLovCode(dto, errorMessage, fieldName) : this;
    }

    /** Una LOV es utilizable si se puede resolver por id o por código. */
    public ValidatorUtils validateRequiredLovDTO(LovDTO dto, String errorMessage, String fieldName) {
        if (dto == null || (dto.getId() == null && StringUtils.isBlank(dto.getCode()))) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateRequiredLovDTOIfTrueCondition(boolean condition, LovDTO dto, String errorMessage, String fieldName) {
        return condition ? validateRequiredLovDTO(dto, errorMessage, fieldName) : this;
    }

    // --- Longitud, tamaño y formato ---

    public ValidatorUtils validateLengthField(Object field, int min, int max, String errorMessage, String fieldName) {
        String value = switch (field) {
            case Integer i -> i.toString();
            case Long l -> l.toString();
            case String s -> s;
            case null, default -> null;
        };

        if (value != null && (value.length() < min || value.length() > max)) {
            addDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max));
        }

        return this;
    }

    public ValidatorUtils validateLengthFieldIfTrueCondition(boolean condition, Object field, int min, int max, String errorMessage, String fieldName) {
        return condition ? validateLengthField(field, min, max, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateLengthLovCodeIfTrueCondition(boolean condition, LovDTO field, int min, int max, String errorMessage, String fieldName) {
        if (condition && field != null && field.getCode() != null) {
            return validateLengthField(field.getCode(), min, max, errorMessage, fieldName);
        }

        return this;
    }

    /** Número máximo de elementos de una colección. Una colección nula o vacía siempre pasa. */
    public ValidatorUtils validateMaxSize(Collection<?> collection, int max, String errorMessage, String fieldName) {
        if (CollectionUtils.size(collection) > max) {
            addDanger(errorMessage, fieldName, "0", String.valueOf(max));
        }

        return this;
    }

    public ValidatorUtils validateFormat(String field, String regex, String errorMessage, String fieldName) {
        if (field == null || regex == null) {
            return this;
        }

        if (!field.matches(regex)) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateFormatIfTrueCondition(boolean condition, String field, String regex, String errorMessage, String fieldName) {
        return condition ? validateFormat(field, regex, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateNumericField(Object field, String errorMessage, String fieldName) {
        if (field instanceof String stringValue && !NUMERIC_PATTERN.matcher(stringValue).matches()) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateNumericFieldIfTrueCondition(boolean condition, Object field, String errorMessage, String fieldName) {
        return condition ? validateNumericField(field, errorMessage, fieldName) : this;
    }

    /**
     * Dígito de control de un código de contenedor ISO 6346. Un código con longitud, alfabeto o
     * dígitos inválidos genera alerta en vez de propagar una excepción.
     */
    public ValidatorUtils validateContainerControlDigit(String field, String errorMessage, String fieldName) {
        return validateContainerControlDigit(true, field, errorMessage, fieldName);
    }

    public ValidatorUtils validateContainerControlDigit(boolean condition, String field, String errorMessage, String fieldName) {
        if (!condition || field == null) {
            return this;
        }

        if (!isValidContainerCode(field)) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    // --- Rangos numéricos ---

    public ValidatorUtils validateRange(Integer value, int min, int max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            addDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, Integer value, int min, int max, String errorMessage, String fieldName) {
        return condition ? validateRange(value, min, max, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateRange(Long value, long min, long max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            addDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, Long value, long min, long max, String errorMessage, String fieldName) {
        return condition ? validateRange(value, min, max, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateRange(Double value, double min, double max, String errorMessage, String fieldName) {
        if (value != null && (value < min || value > max)) {
            addDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, Double value, double min, double max, String errorMessage, String fieldName) {
        return condition ? validateRange(value, min, max, errorMessage, fieldName) : this;
    }

    public ValidatorUtils validateRange(BigDecimal value, BigDecimal min, BigDecimal max, String errorMessage, String fieldName) {
        if (value == null) {
            return this;
        }

        boolean belowMin = min != null && value.compareTo(min) < 0;
        boolean aboveMax = max != null && value.compareTo(max) > 0;

        if (belowMin || aboveMax) {
            addDanger(errorMessage, fieldName, String.valueOf(min), String.valueOf(max));
        }

        return this;
    }

    public ValidatorUtils validateRangeIfTrueCondition(boolean condition, BigDecimal value, BigDecimal min, BigDecimal max, String errorMessage, String fieldName) {
        return condition ? validateRange(value, min, max, errorMessage, fieldName) : this;
    }

    // --- Precisión decimal ---

    /**
     * Comprueba dígitos enteros y decimales partiendo la representación en texto. El signo no
     * cuenta como dígito entero.
     */
    public ValidatorUtils validateBigDecimalWithSplit(BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        if (value == null) {
            return this;
        }

        String[] parts = value.abs().toPlainString().split("\\.");
        String integerPart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "";

        if (integerPart.length() > maxIntegers || decimalPart.length() > maxDecimals) {
            addDanger(errorMessage, fieldName, String.valueOf(maxIntegers), String.valueOf(maxDecimals));
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalWithSplitIfTrueCondition(boolean condition, BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        return condition ? validateBigDecimalWithSplit(value, maxIntegers, maxDecimals, errorMessage, fieldName) : this;
    }

    /**
     * Equivalente a {@code @Digits(integer = maxIntegers, fraction = maxDecimals)}: comprueba la
     * precisión declarada de la columna sin depender de la representación textual.
     */
    public ValidatorUtils validateBigDecimalWithPrecision(BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        if (value == null) {
            return this;
        }

        int scale = Math.max(value.scale(), 0);
        int integerDigits = value.precision() - value.scale();

        if (integerDigits > maxIntegers || scale > maxDecimals) {
            addDanger(errorMessage, fieldName, String.valueOf(maxIntegers), String.valueOf(maxDecimals));
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalWithPrecisionIfTrueCondition(boolean condition, BigDecimal value, int maxIntegers, int maxDecimals, String errorMessage, String fieldName) {
        return condition ? validateBigDecimalWithPrecision(value, maxIntegers, maxDecimals, errorMessage, fieldName) : this;
    }

    /**
     * Igualdad numérica de dos importes. Coherente con el resto de la clase: si alguno es nulo no
     * hay nada que comparar y no se genera alerta.
     */
    public ValidatorUtils validateBigDecimalEquality(BigDecimal value1, BigDecimal value2, String errorMessage, String fieldName) {
        if (value1 != null && value2 != null && value1.compareTo(value2) != 0) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateBigDecimalEqualityIfTrueCondition(boolean condition, BigDecimal value1, BigDecimal value2, String errorMessage, String fieldName) {
        return condition ? validateBigDecimalEquality(value1, value2, errorMessage, fieldName) : this;
    }

    // --- Fechas ---

    public ValidatorUtils validateDate1IsAfterDate2(LocalDate date1, LocalDate date2, String dateErrorMessage, String fieldName) {
        if (date1 != null && date2 != null && date1.isBefore(date2)) {
            addDanger(dateErrorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateDate1IsAfterDate2(Date date1, Date date2, String dateErrorMessage, String timeErrorMessage, String fieldName) {
        if (date1 == null || date2 == null) {
            return this;
        }

        LocalDate d1 = toLocalDate(date1);
        LocalDate d2 = toLocalDate(date2);

        if (d1.isBefore(d2)) {
            addDanger(dateErrorMessage, fieldName);
        } else if (toOffsetDateTime(date1).isBefore(toOffsetDateTime(date2))) {
            addDanger(timeErrorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateDateIsAfterNow(Date date, String dateErrorMessage, String timeErrorMessage, String fieldName) {
        if (date == null) {
            return this;
        }

        if (toLocalDate(date).isBefore(LocalDate.now())) {
            addDanger(dateErrorMessage, fieldName);
        } else if (toOffsetDateTime(date).isBefore(OffsetDateTime.now())) {
            addDanger(timeErrorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateDateIsBeforeNow(Date date, String dateErrorMessage, String timeErrorMessage, String fieldName) {
        if (date == null) {
            return this;
        }

        if (toLocalDate(date).isAfter(LocalDate.now())) {
            addDanger(dateErrorMessage, fieldName);
        } else if (toOffsetDateTime(date).isAfter(OffsetDateTime.now())) {
            addDanger(timeErrorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateDateIsBeforeNow(LocalDateTime date, String errorMessage, String fieldName) {
        if (date != null && date.isAfter(LocalDateTime.now())) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateDateIsAfterNow(LocalDateTime date, String errorMessage, String fieldName) {
        if (date != null && date.isBefore(LocalDateTime.now())) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateLocalDate1IsBeforeDate2(LocalDateTime date1, LocalDateTime date2, String errorMessage, String fieldName) {
        if (date1 != null && date2 != null && date1.isAfter(date2)) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateLocalDate1IsAfterDate2(LocalDateTime date1, LocalDateTime date2, String errorMessage, String fieldName) {
        if (date1 != null && date2 != null && date1.isBefore(date2)) {
            addDanger(errorMessage, fieldName);
        }

        return this;
    }

    public ValidatorUtils validateLocalDateTime1IsAfterDateTime2(LocalDateTime date1, LocalDateTime date2,
                                                                 String dateErrorMessage, String timeErrorMessage, String fieldName) {
        if (date1 == null || date2 == null) {
            return this;
        }

        if (date1.toLocalDate().isBefore(date2.toLocalDate())) {
            addDanger(dateErrorMessage, fieldName);
        } else if (date1.isBefore(date2)) {
            addDanger(timeErrorMessage, fieldName);
        }

        return this;
    }

    // --- Internos ---

    private void addDanger(String errorMessage, String... fields) {
        alerts.add(Alert.ofDanger(errorMessage, fields));
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static OffsetDateTime toOffsetDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static boolean isValidContainerCode(String code) {
        if (code.length() != CONTAINER_CODE_LENGTH) {
            return false;
        }

        int sum = 0;

        for (int i = 0; i < CONTAINER_CODE_LENGTH - 1; i++) {
            char character = code.charAt(i);
            Integer value = i < 4
                    ? CONTAINER_LETTER_CODES.get(character)
                    : digitOrNull(character);

            if (value == null) {
                return false;
            }

            sum += (int) (value * Math.pow(2, i));
        }

        Integer controlDigit = digitOrNull(code.charAt(CONTAINER_CODE_LENGTH - 1));

        return controlDigit != null && sum % 11 % 10 == controlDigit;
    }

    private static Integer digitOrNull(char character) {
        return Character.isDigit(character) ? Character.digit(character, 10) : null;
    }

    private static Map<Character, Integer> buildContainerLetterCodes() {
        Map<Character, Integer> codes = new HashMap<>();

        int code = 10;

        for (char character = 'A'; character <= 'Z'; character++) {
            if (code % 11 == 0) {
                code++;
            }

            codes.put(character, code);
            code++;
        }

        return Map.copyOf(codes);
    }
}
