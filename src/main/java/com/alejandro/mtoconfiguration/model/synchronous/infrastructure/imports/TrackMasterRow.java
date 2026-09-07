package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

/**
 * Una fila de la hoja TRACKS. La clave natural es {@code (paquete, nombre)}.
 *
 * @param station nombre de la estacion, <b>o vacio</b>. Vacio no es un olvido:
 *                {@code TRACK.STATION_ID} es anulable a proposito y una via de tramo
 *                entre estaciones cuelga directamente del paquete de ejecucion.
 */
public record TrackMasterRow(
        String executionPackage,
        String name,
        String station,
        boolean enabled,
        int sourceRow
) {
}
