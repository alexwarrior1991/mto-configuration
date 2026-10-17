package com.alejandro.mtoconfiguration.enums.infrastructure;

import java.io.Serializable;

/**
 * Por qué lado de la vía se entra desde un punto de acceso, en el sentido creciente del KP.
 *
 * <p>Enum por lo mismo que {@link TrackAccessPointKind}: tres casos cerrados que viajan con su nombre
 * en el evento {@code track-access-point}.
 */
public enum TrackAccessPointSide implements Serializable {

    /** Por la izquierda. */
    LEFT,

    /** Por la derecha. */
    RIGHT,

    /** Por los dos lados: un paso a nivel, casi siempre. */
    BOTH;

    /** Lectura tolerante: lo que no se reconoce es {@code null}, no una excepción. */
    public static TrackAccessPointSide fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        for (TrackAccessPointSide value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }

        return null;
    }
}
