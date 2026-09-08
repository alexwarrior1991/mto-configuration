package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TrackDTO extends BaseDTO {

    private String name;
    private Boolean enabled;
    private Long executionPackageId;
    /**
     * Estaciones que atraviesa la via. Vacia es una respuesta valida: un tramo entre
     * estaciones cuelga directamente del paquete de ejecucion.
     */
    private List<Long> stationIds = new ArrayList<>();
    private List<ProfileDTO> profiles = new ArrayList<>();
}
