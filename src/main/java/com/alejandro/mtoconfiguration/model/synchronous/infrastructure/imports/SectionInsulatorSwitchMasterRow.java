package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.math.BigDecimal;

/**
 * Una fila de la hoja SECTION_INSULATOR_SWITCHES: una aguja de un aislador de sección.
 *
 * <p>Se identifica dentro de su aislador por el {@code code} ({@code W31}), que es lo que hace
 * determinista la reconciliación al reimportar: sin él una aguja no tiene con qué identificarse,
 * porque {@code SectionInsulatorSwitch.equals} es sólo por id.
 *
 * @param sectionInsulator nombre del aislador al que pertenece, dentro de {@code station}
 * @param code             {@code W31}
 * @param kp               punto kilométrico de la aguja, en metros. Puede ser nulo: el plano lo
 *                         rotula una vez para un grupo de agujas y no lo repite en cada una
 * @param turnoutDenominator el {@code 9} de {@code 1:9}. Puede ser nulo
 * @param track            vía a la que llega esta conexión, por nombre. Puede ser nula
 */
public record SectionInsulatorSwitchMasterRow(
        String executionPackage,
        String station,
        String sectionInsulator,
        String code,
        BigDecimal kp,
        Integer turnoutDenominator,
        String track,
        boolean enabled,
        int sourceRow
) {
}
