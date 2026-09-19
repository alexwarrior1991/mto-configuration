package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SectionInsulatorDTO extends BaseDTO {

    private String name;
    private Boolean enabled;
    private Long stationId;

    /** KP del aislador, en metros. */
    private BigDecimal kp;

    /** Dos vías que conectan ({@code TRACK_CONNECTION}) o en medio de una ({@code IN_TRACK}). */
    private SectionInsulatorInstallationType installationType;

    private Long trackId;

    /** Vía con la que conecta. Nula en un {@code IN_TRACK}. */
    private Long connectedTrackId;

    /**
     * Agujas de la conexión.
     *
     * <p>Inicializada a lista vacía, como el resto de colecciones de hijos de la API: mandar la
     * colección es declarar cuáles son TODAS las agujas del aislador, y omitirla en el JSON deja
     * la lista vacía, que borra las que hubiera. Ver {@code README_API.md} §4 y
     * {@code BaseMapper.mergeCollection}, donde {@code null} —que sólo puede construir un cliente
     * Java, no un JSON— es lo único que la deja intacta.
     */
    private List<SectionInsulatorSwitchDTO> switches = new ArrayList<>();
}
