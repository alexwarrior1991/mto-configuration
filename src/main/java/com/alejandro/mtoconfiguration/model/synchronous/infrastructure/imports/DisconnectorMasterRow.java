package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.math.BigDecimal;

/**
 * Una fila de la hoja DISCONNECTORS: un seccionador de una estación, en un poste o sin él.
 *
 * <p>La clave natural es {@code (paquete, estación, nombre)}, la misma que la del aislador de
 * sección: el maestro no trae identificadores técnicos.
 *
 * <p>Los SI/NO y el accionamiento llegan tal cual se escribieron en la celda. Los interpreta el
 * upsert, que es donde una celda que no se entiende puede señalar su fila en el informe; el
 * parser solo podría tumbar el fichero entero o callarse.
 *
 * @param track                con poste, la vía del poste, que es donde se busca; sin poste, la vía
 *                             del propio seccionador (V26). Por nombre: el id lo resuelve el
 *                             importador
 * @param profileId            identificador del poste del que cuelga, o en blanco si no está en un
 *                             poste
 * @param profileKp            KP del poste, en metros. Solo decide cuál es el poste cuando la vía
 *                             repite su identificador (una vía de dos tramos concatenados)
 * @param kp                   KP propio, en metros, solo sin poste. Como texto, igual que en el
 *                             DTO, para que el validador lo compruebe y señale el campo
 * @param onLoad               SI (en carga) o NO
 * @param normallyOpen         SI (normalmente abierto), NO (normalmente cerrado) o en blanco
 * @param driveType            {@code MOTOR}, {@code MANUAL} o en blanco
 * @param disconnectorFunction código del catálogo DisconnectorFunction
 */
public record DisconnectorMasterRow(
        String executionPackage,
        String station,
        String track,
        String profileId,
        BigDecimal profileKp,
        String name,
        String kp,
        String onLoad,
        String normallyOpen,
        String driveType,
        String disconnectorFunction,
        boolean enabled,
        int sourceRow
) {
}
