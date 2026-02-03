package com.alejandro.mtoconfiguration.validator.commons.validator;

import com.alejandro.mtoconfiguration.validator.commons.annotation.LocalDateRelative;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class LocalDateRelativeValidator implements ConstraintValidator<LocalDateRelative, LocalDateTime> {

    private long minOffsetMinutes;
    private long maxOffsetMinutes;
    private boolean minInclusive;
    private boolean maxInclusive;

    // Para poder testear fácilmente (inyectar un "ahora" fijo en tests)
    private Supplier<LocalDateTime> nowSupplier = () -> LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);


    @Override
    public void initialize(LocalDateRelative constraintAnnotation) {
        this.minOffsetMinutes = constraintAnnotation.minOffsetMinutes();
        this.maxOffsetMinutes = constraintAnnotation.maxOffsetMinutes();
        this.minInclusive = constraintAnnotation.minInclusive();
        this.maxInclusive = constraintAnnotation.maxInclusive();
    }

    @Override
    public boolean isValid(LocalDateTime localDateTime, ConstraintValidatorContext constraintValidatorContext) {
        return localDateTime == null
                || buildPredicate().test(localDateTime);
    }

    private Predicate<LocalDateTime> buildPredicate() {
        LocalDateTime now = nowSupplier.get();
        LocalDateTime minDate = now.plusMinutes(minOffsetMinutes);
        LocalDateTime maxDate = now.plusMinutes(maxOffsetMinutes);

        Predicate<LocalDateTime> minPredicate = minPredicate(minDate);
        Predicate<LocalDateTime> maxPredicate = maxPredicate(maxDate);

        return minPredicate.and(maxPredicate);

    }

    private Predicate<LocalDateTime> minPredicate(LocalDateTime minDate) {
        // Si minOffsetMinutes es Long.MIN_VALUE, lo interpretamos como "sin mínimo"
        if (minOffsetMinutes == Long.MIN_VALUE) {
            return date -> true;
        }

        return minInclusive
                ? date -> !date.isBefore(minDate)   // date >= minDate
                : date -> date.isAfter(minDate);    // date > minDate
    }

    private Predicate<LocalDateTime> maxPredicate(LocalDateTime maxDate) {
        // Si maxOffsetMinutes es Long.MAX_VALUE, lo interpretamos como "sin máximo"
        if (maxOffsetMinutes == Long.MAX_VALUE) {
            return date -> true;
        }

        return maxInclusive
                ? date -> !date.isAfter(maxDate)    // date <= maxDate
                : date -> date.isBefore(maxDate);   // date < maxDate
    }

    // (Opcional) solo para tests, inyectar un "ahora" fijo
    void setNowSupplier(Supplier<LocalDateTime> nowSupplier) {
        this.nowSupplier = nowSupplier;
    }
}
