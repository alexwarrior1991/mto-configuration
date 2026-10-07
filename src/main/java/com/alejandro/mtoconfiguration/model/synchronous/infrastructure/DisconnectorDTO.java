package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.enums.infrastructure.DisconnectorDriveType;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DisconnectorDTO extends BaseDTO {

    private String name;
    private Boolean onLoad;

    /**
     * Estado normal: {@code true} normalmente abierto, {@code false} normalmente cerrado y
     * {@code null} sin dato.
     */
    private Boolean normallyOpen;

    /** Accionamiento: {@code MOTOR}, {@code MANUAL} o {@code null} sin dato. */
    private DisconnectorDriveType driveType;

    private Long stationId;

    /** Poste del que cuelga. Opcional: un seccionador que no está en un poste lo deja a {@code null}. */
    private Long profileId;

    /**
     * KP en metros y vía, <b>solo</b> de un seccionador sin poste: los de uno en un poste son los de
     * su perfil (V26). El KP viaja como texto, como el del perfil, porque este DTO pasa por la caché,
     * que no lee decimales.
     */
    private String kp;
    private Long trackId;

    /**
     * La otra vía de un seccionador que pone dos en paralelo (V27), con poste o sin él. Opcional, y
     * distinta de la suya.
     */
    private Long connectedTrackId;

    /**
     * Identificador y KP del perfil al que cuelga, <b>solo de salida</b>: los rellena el mapper
     * para que una lista de seccionadores se lea sin ir perfil por perfil (son miles). Al
     * escribir se ignoran: el perfil se elige por {@code profileId}.
     */
    private String profileCode;
    private String profileKp;

    private DisconnectorFunctionDTO disconnectorFunction;
}
