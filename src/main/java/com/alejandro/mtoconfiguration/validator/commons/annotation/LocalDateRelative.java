package com.alejandro.mtoconfiguration.validator.commons.annotation;

import com.alejandro.mtoconfiguration.validator.commons.validator.LocalDateRelativeValidator;
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
@Constraint(validatedBy = LocalDateRelativeValidator.class)
public @interface LocalDateRelative {


    String message() default "La fecha no cumple las restricciones relativas a la fecha actual";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Offset mínimo respecto a ahora, en minutos.
     * Ej: 0 = no antes de ahora, -1440 = como máximo 1 día en el pasado.
     */
    long minOffsetMinutes() default Long.MIN_VALUE;

    /**
     * Offset máximo respecto a ahora, en minutos.
     * Ej: 0 = no después de ahora, 1440 = como máximo 1 día en el futuro.
     */
    long maxOffsetMinutes() default Long.MAX_VALUE;

    /**
     * ¿El mínimo es inclusivo? (>= ahora + minOffset)
     */
    boolean minInclusive() default true;

    /**
     * ¿El máximo es inclusivo? (<= ahora + maxOffset)
     */
    boolean maxInclusive() default true;
}
