package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.math.BigDecimal;

/**
 * Una fila de la hoja CANTILEVERS: una mensula de un perfil.
 *
 * @param slot            1, 2 o 3. Es la posicion en las columnas M1/M2/M3 del origen y
 *                        lo que hace determinista la reconciliacion al reimportar: sin
 *                        el, una mensula no tiene con que identificarse dentro de su
 *                        perfil ({@code Cantilever.equals} es solo por id).
 * @param steadyArmLength <b>puede ser nula</b>. El origen trae 5.691 brazos con el tipo
 *                        y sin la longitud: no se conoce.
 */
public record CantileverMasterRow(
        String executionPackage,
        String track,
        String profileId,
        Integer orderInTrack,
        int slot,
        String cantileverType,
        BigDecimal stagger,
        BigDecimal catenaryHeight,
        BigDecimal cwElevation,
        BigDecimal cwHeight,
        BigDecimal windDeflection,
        BigDecimal armAngle,
        String steadyArmType,
        Long steadyArmLength,
        boolean enabled,
        int sourceRow
) {
}
