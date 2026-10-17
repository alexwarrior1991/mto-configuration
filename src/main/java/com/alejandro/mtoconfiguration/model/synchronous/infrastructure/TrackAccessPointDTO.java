package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Punto de acceso a la vía (V28).
 *
 * <p>Los decimales viajan como número, como el KP del aislador, y no como texto como el del perfil:
 * este DTO no pasa por la caché ({@code TrackAccessPointService.isCacheable()}), que es lo único que
 * obligaba a escribirlos como texto.
 */
@Getter
@Setter
public class TrackAccessPointDTO extends BaseDTO {

    private String name;

    /** La vía a la que da acceso. Obligatoria. */
    private Long trackId;

    /** La estación, si está en una. Opcional. */
    private Long stationId;

    /** KP en metros, como el del aislador. Opcional. */
    private BigDecimal kp;

    /** {@code PREPARED_ACCESS}, {@code LEVEL_CROSSING} o {@code DEPOT}. Obligatorio. */
    private TrackAccessPointKind kind;

    /** {@code LEFT}, {@code RIGHT} o {@code BOTH}. Obligatorio. */
    private TrackAccessPointSide side;

    /** Si sirve para encarrilar biviales. Un cuerpo que no lo trae vale {@code true}. */
    private Boolean roadRailAccess = Boolean.TRUE;

    /** Carga por eje y peso bruto máximos, en toneladas. Opcionales y no negativos. */
    private BigDecimal maxAxleLoadT;
    private BigDecimal maxGrossWeightT;

    /** Coordenadas en grados decimales (WGS 84). Opcionales, pero las dos o ninguna. */
    private BigDecimal latitude;
    private BigDecimal longitude;

    /** Un cuerpo que no lo trae vale {@code true}, como el de la entidad. */
    private Boolean enabled = Boolean.TRUE;
}
