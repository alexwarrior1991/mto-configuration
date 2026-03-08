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

    private DisconnectorFunctionDTO disconnectorFunction;
}
