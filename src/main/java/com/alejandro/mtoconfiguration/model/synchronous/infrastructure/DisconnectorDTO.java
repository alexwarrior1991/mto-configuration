package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DisconnectorDTO extends BaseDTO {

    private String name;
    private Boolean onLoad;

    private Long stationId;
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
