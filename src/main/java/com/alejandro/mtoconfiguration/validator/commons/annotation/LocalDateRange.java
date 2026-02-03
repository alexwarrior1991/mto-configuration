package com.alejandro.mtoconfiguration.validator.commons.annotation;

import com.alejandro.mtoconfiguration.validator.commons.validator.LocalDateRangeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = LocalDateRangeValidator.class)
public @interface LocalDateRange {

    String message() default "La fecha está fuera del rango permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Fecha mínima permitida en formato ISO-8601:
     * Ej: "2025-01-01T00:00:00"
     * Si se deja vacío, no hay límite inferior.
     */
    String min() default "";

    /**
     * Fecha máxima permitida en formato ISO-8601:
     * Ej: "2025-12-31T23:59:59"
     * Si se deja vacío, no hay límite superior.
     */
    String max() default "";

    /**
     * ¿El límite mínimo es inclusivo? (>= min)
     */
    boolean minInclusive() default true;

    /**
     * ¿El límite máximo es inclusivo? (<= max)
     */
    boolean maxInclusive() default true;
}
