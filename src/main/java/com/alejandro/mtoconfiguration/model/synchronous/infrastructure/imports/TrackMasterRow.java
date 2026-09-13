package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.util.List;

/**
 * Una fila de la hoja TRACKS. La clave natural es {@code (paquete, nombre)}.
 *
 * @param stations nombres de las estaciones que atraviesa la via, <b>o vacio</b>. Vacio no
 *                 es un olvido: una via de tramo entre estaciones cuelga directamente del
 *                 paquete de ejecucion. Y son varias porque una via larga atraviesa varias
 *                 estaciones sin dejar de ser una via: 'TRACK 1' de EP4 pasa por ZIC, por
 *                 BIN y por HAD.
 */
public record TrackMasterRow(
        String executionPackage,
        String name,
        List<String> stations,
        boolean enabled,
        int sourceRow
) {
}
