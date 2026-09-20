package com.alejandro.mtoconfiguration.enums.infrastructure;

import java.io.Serializable;

/**
 * Cómo está puesto un aislador de sección sobre la vía.
 *
 * <p>Enum y no lista de valores: son dos casos cerrados que describe el propio plano, no un
 * catálogo que alguien vaya a ampliar desde el mantenimiento de LOV. Se persiste como texto
 * ({@code @Enumerated(STRING)}) para que la columna se lea sola en una consulta y para que añadir
 * un valor no dependa del orden de declaración.
 */
public enum SectionInsulatorInstallationType implements Serializable {

    /**
     * El caso normal: el aislador separa las catenarias de <b>dos vías</b> que conectan por una
     * aguja. Lleva vía principal, vía conectada y las agujas de la conexión.
     */
    TRACK_CONNECTION,

    /**
     * El aislador está en medio de <b>una sola vía</b>, partiendo su catenaria en dos secciones de
     * alimentación. No hay vía conectada.
     */
    IN_TRACK;

    /**
     * Lectura tolerante: lo que no se reconoce es {@code null}, no una excepción.
     *
     * <p>La usan el importador del maestro y la API, que reciben el valor como texto desde fuera.
     * Reventar aquí convertiría una celda mal escrita del workbook en un fallo del importador
     * entero en vez de en una fila señalada en el informe.
     */
    public static SectionInsulatorInstallationType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        for (SectionInsulatorInstallationType value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }

        return null;
    }
}
