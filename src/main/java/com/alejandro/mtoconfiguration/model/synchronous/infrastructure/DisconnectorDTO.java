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
     * Identificador y KP del perfil al que cuelga, <b>solo de salida</b>: los rellena el mapper
     * para que una lista de seccionadores se lea sin ir perfil por perfil (son miles). Al
     * escribir se ignoran: el perfil se elige por {@code profileId}.
     */
    private String profileCode;
    private String profileKp;

    private DisconnectorFunctionDTO disconnectorFunction;
}
