package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Aguja por la que el aislador de sección conecta con una vía: {@code W31}, en su KP y con la
 * tangente de su desvío.
 */
@Getter
@Setter
public class SectionInsulatorSwitchDTO extends BaseDTO {

    /** Identificador de la aguja en el plano: {@code W31}. */
    private String code;

    /** Punto kilométrico de la aguja, en metros. El plano escribe {@code 110+176}. */
    private BigDecimal kp;

    /** El {@code 9} de {@code 1:9}. El numerador siempre es 1, así que no viaja. */
    private Integer turnoutDenominator;

    private Long trackId;

    private Boolean enabled;

    /**
     * La tangente tal y como está escrita en el plano, sólo de salida.
     *
     * <p>Lo que se guarda y lo que se manda es el denominador, que es lo único que varía y lo único
     * que se puede ordenar; esto ahorra a cada consumidor componer la misma cadena.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getTurnoutRate() {
        return turnoutDenominator == null ? null : "1:" + turnoutDenominator;
    }
}
