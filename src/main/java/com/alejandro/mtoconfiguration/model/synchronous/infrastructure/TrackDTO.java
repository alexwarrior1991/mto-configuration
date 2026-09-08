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
     * Estaciones que atraviesa la via. Lista vacia es una respuesta valida: un tramo entre
     * estaciones cuelga directamente del paquete de ejecucion.
     *
     * <p>Sin inicializar a lista vacia, y no es un descuido: aqui {@code null} significa "el
     * cliente no ha mandado el campo" y deja las estaciones como estan, mientras que la lista
     * vacia SI desliga. Con el {@code = new ArrayList<>()} que tenia, una via anidada dentro de
     * una estacion —que nunca trae este campo— llegaba al mapper con la lista vacia y borraba
     * las estaciones de esa via, incluidas las otras dos por las que pasa. Las tres listas N:M
     * de ProfileDTO siguen el mismo criterio.
     */
    private List<Long> stationIds;
    private List<ProfileDTO> profiles = new ArrayList<>();
}
