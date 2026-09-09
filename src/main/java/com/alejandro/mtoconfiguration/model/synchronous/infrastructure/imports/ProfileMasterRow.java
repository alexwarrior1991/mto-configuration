package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.math.BigDecimal;

/**
 * Una fila de la hoja PROFILES. La clave natural es {@code (via, profileId, kp)}.
 *
 * @param kp           viaja como texto porque asi lo declara {@code ProfileDTO}: la
 *                     validacion de formato vive en {@code ProfileValidator} y se quiere
 *                     ejecutar tal cual. Desde V18 forma parte de la clave natural: una via
 *                     con dos tramos concatenados repite el identificador de perfil, y el KP
 *                     es lo que distingue un mastil del otro.
 * @param orderInTrack posicion 1..N a lo largo de la via, en el orden del origen. Es lo que
 *                     ordena los perfiles desde V18, porque con dos tramos concatenados el KP
 *                     ya no vale: el segundo reinicia la kilometracion.
 * @param span         vano <b>hasta el perfil siguiente</b>, en metros.
 */
public record ProfileMasterRow(
        String executionPackage,
        String track,
        String profileId,
        String kp,
        Integer orderInTrack,
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
