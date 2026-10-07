package com.alejandro.mtoconfiguration.validator.infrastructure;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

/**
 * Un KP que viaja como texto en el DTO: el del perfil y el de un seccionador sin poste. Viajan como
 * texto y no como {@code BigDecimal} porque los dos DTO pasan por la caché, que no lee decimales.
 */
final class KpText {

    /** En metros, sin signo y con punto decimal: {@code 98375} o {@code 98375.5}. */
    static final String PATTERN = "\\d+(\\.\\d+)?";

    private KpText() {
    }

    /** El número para comprobar su precisión; {@code null} si no hay o no es un número. */
    static BigDecimal parse(String kp) {
        if (StringUtils.isBlank(kp)) {
            return null;
        }

        try {
            return new BigDecimal(kp.trim());
        } catch (NumberFormatException e) {
            // El formato ya lo reporta validateFormat; aquí no hay precisión que comprobar.
            return null;
        }
    }
}
