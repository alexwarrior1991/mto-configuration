package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.math.BigDecimal;

/**
 * Una fila de la hoja SECTION_INSULATORS: un aislador de sección de una estación.
 *
 * <p>La clave natural es {@code (paquete, estación, nombre)}: el maestro no trae identificadores
 * técnicos, así que es lo único con lo que la reimportación puede reconocer la fila que ya existe.
 *
 * @param kp               punto kilométrico del aislador, en metros. Puede ser nulo: en una
 *                         conexión entre vías el aislador está realmente en los KP de sus agujas
 * @param installationType {@code TRACK_CONNECTION} o {@code IN_TRACK}. Puede ser nulo
 * @param track            vía principal, por nombre. El id lo resuelve el upsert
 * @param connectedTrack   vía con la que conecta. Nula en un {@code IN_TRACK}
 */
public record SectionInsulatorMasterRow(
        String executionPackage,
        String station,
        String name,
        BigDecimal kp,
        String installationType,
        String track,
        String connectedTrack,
        boolean enabled,
        int sourceRow
) {
}
