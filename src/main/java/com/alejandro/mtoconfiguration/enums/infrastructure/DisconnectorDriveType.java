package com.alejandro.mtoconfiguration.enums.infrastructure;

import java.io.Serializable;

/**
 * Cómo se acciona un seccionador.
 *
 * <p>Enum y no lista de valores, por lo mismo que {@link SectionInsulatorInstallationType}: son
 * los dos casos que distingue la leyenda del plano de seccionamiento, no un catálogo que alguien
 * vaya a ampliar desde el mantenimiento de LOV. Se persiste como texto
 * ({@code @Enumerated(STRING)}) para que la columna se lea sola en una consulta y para que añadir
 * un valor no dependa del orden de declaración.
 */
public enum DisconnectorDriveType implements Serializable {

    /** Con motor: el plano lo dibuja con el círculo del accionamiento junto a la cuchilla. */
    MOTOR,

    /** Sin motor: se maniobra a mano en el propio seccionador. */
    MANUAL;

    /**
     * Lectura tolerante, como {@link SectionInsulatorInstallationType#fromCode(String)}: lo que no se
     * reconoce es {@code null}, no una excepción.
     *
     * <p>La usa el importador del maestro, que recibe el valor como texto de la hoja DISCONNECTORS y
     * es quien decide qué hacer con una celda que no dice ni {@code MOTOR} ni {@code MANUAL}:
     * señalar esa fila en el informe, no tumbar la importación entera.
     */
    public static DisconnectorDriveType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        for (DisconnectorDriveType value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }

        return null;
    }
}
