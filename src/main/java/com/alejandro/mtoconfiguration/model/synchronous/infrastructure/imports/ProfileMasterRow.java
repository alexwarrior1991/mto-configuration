package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.math.BigDecimal;

/**
 * Una fila de la hoja PROFILES. La clave natural es {@code (via, profileId)}.
 *
 * @param kp   viaja como texto porque asi lo declara {@code ProfileDTO}: la validacion
 *             de formato vive en {@code ProfileValidator} y se quiere ejecutar tal cual.
 * @param span vano <b>hasta el perfil siguiente</b>, en metros.
 */
public record ProfileMasterRow(
        String executionPackage,
        String track,
        String profileId,
        String kp,
        String profileStatus,
        ProfileLovCodes lov,
        BigDecimal span,
        BigDecimal heightCantileverSupport,
        BigDecimal poleGaugeLocation,
        BigDecimal railPoleDistance,
        boolean enabled,
        int sourceRow
) {
}
