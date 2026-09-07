package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import java.time.LocalDate;

/**
 * Una fila de la hoja EPS.
 *
 * <p>Ninguno de estos metadatos esta en los workbooks: los declara una persona en
 * {@code data/tools/topology.yml} y el generador los copia al maestro.
 *
 * @param code identificador del workbook ({@code EP6}), que es la clave con la que el
 *             resto de hojas referencian al paquete. No se guarda: sirve para enlazar
 *             dentro del maestro.
 * @param name nombre del paquete en base de datos, y su clave natural.
 */
public record ExecutionPackageMasterRow(
        String code,
        String name,
        boolean initialPackage,
        Long length,
        LocalDate startDate,
        LocalDate endDate,
        String companyIdentificationNumber,
        boolean enabled,
        int sourceRow
) {
}
