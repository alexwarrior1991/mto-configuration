package com.alejandro.mtoconfiguration.validator.commons.validator;

import com.alejandro.mtoconfiguration.validator.commons.annotation.LocalDateRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Predicate;

public class LocalDateRangeValidator implements ConstraintValidator<LocalDateRange, LocalDateTime> {

    private LocalDateTime min;
    private LocalDateTime max;
    private boolean minInclusive;
    private boolean maxInclusive;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void initialize(LocalDateRange constraintAnnotation) {

        String minStr = constraintAnnotation.min();
        String maxStr = constraintAnnotation.max();

        this.minInclusive = constraintAnnotation.minInclusive();
        this.maxInclusive = constraintAnnotation.maxInclusive();

        this.min = parseIfNotEmpty(minStr);
        this.max = parseIfNotEmpty(maxStr);


        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(LocalDateTime localDateTime, ConstraintValidatorContext constraintValidatorContext) {
        // null se considera válido; @NotNull se maneja aparte
        return localDateTime == null
                || (minPredicate().and(maxPredicate()).test(localDateTime));
    }

    private LocalDateTime parseIfNotEmpty(String value) {

        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Valor de fecha inválido en @FechaEnRango: " + value +
                            ". Usa formato ISO-8601: yyyy-MM-dd'T'HH:mm:ss", e
            );
        }

    }

    /**
     * Predicado para validar el mínimo (si existe).
     */
    private Predicate<LocalDateTime> minPredicate() {
        return Optional.ofNullable(min)
                .<Predicate<LocalDateTime>>map(minValue ->
                        minInclusive
                                ? date -> !date.isBefore(minValue)   // date >= min
                                : date -> date.isAfter(minValue)     // date > min
                )
                .orElse(date -> true); // sin mínimo => siempre pasa
    }

    /**
     * Predicado para validar el máximo (si existe).
     */
    private Predicate<LocalDateTime> maxPredicate() {
        return Optional.ofNullable(max)
                .<Predicate<LocalDateTime>>map(maxValue ->
                        maxInclusive
                                ? date -> !date.isAfter(maxValue)    // date <= max
                                : date -> date.isBefore(maxValue)    // date < max
                )
                .orElse(date -> true); // sin máximo => siempre pasa
    }
}
