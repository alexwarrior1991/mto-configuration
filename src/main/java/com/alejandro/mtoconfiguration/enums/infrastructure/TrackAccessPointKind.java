package com.alejandro.mtoconfiguration.enums.infrastructure;

import java.io.Serializable;

/**
 * Qué clase de punto de acceso a la vía es.
 *
 * <p>Enum y no lista de valores, por lo mismo que {@link SectionInsulatorInstallationType}: son tres
 * casos cerrados del contrato con {@code mto-resources}, que decide con ellos qué vehículo puede entrar
 * por dónde (un modelo que exige acceso preparado solo entra por un {@code PREPARED_ACCESS}). Se
 * persiste como texto ({@code @Enumerated(STRING)}), y el valor viaja con su nombre en el evento
 * {@code track-access-point}: renombrarlo es romper el contrato.
 */
public enum TrackAccessPointKind implements Serializable {

    /** Acceso preparado: rampa o plataforma hecha para encarrilar, con su firme y su camino. */
    PREPARED_ACCESS,

    /** Paso a nivel: se encarrila sobre el propio cruce de la carretera con la vía. */
    LEVEL_CROSSING,

    /** Base o cochera: el acceso desde un recinto propio. */
    DEPOT;

    /**
     * Lectura tolerante, como {@link SectionInsulatorInstallationType#fromCode(String)}: lo que no se
     * reconoce es {@code null}, no una excepción. La usa la búsqueda por criterios, que recibe el
     * filtro como texto.
     */
    public static TrackAccessPointKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        for (TrackAccessPointKind value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }

        return null;
    }
}
