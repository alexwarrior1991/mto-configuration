package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;


import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProfileDTO extends BaseDTO {

    private String profileId;
    private String kp;

    /** Vano hasta el perfil siguiente, en metros. */
    private BigDecimal span;
    /** Altura del soporte de ménsula, en milímetros. */
    private BigDecimal heightCantileverSupport;
    /** Separación del poste respecto al gálibo, en milímetros. */
    private BigDecimal poleGaugeLocation;
    /** Distancia carril-poste, en milímetros. Con signo: indica el lado de la vía. */
    private BigDecimal railPoleDistance;

    private Long trackId;
    private DisconnectorDTO disconnector;
    private List<CantileverDTO> cantilevers = new ArrayList<>();

    /** Anclajes del perfil: <b>varios</b>. Ver {@code sectionings}: mismo motivo. */
    private List<AnchorageDTO> anchorages;
    private AnchorageFoundationDTO anchorageFoundation;
    private FoundationDTO foundation;
    private PoleTypeDTO poleType;
    private PortalDTO portal;
    private ProfileStatusDTO profileStatus;
    private ReturnSupportDTO returnSupport;
    /**
     * Seccionamientos del perfil: <b>varios</b>. Un perfil de estacion puede llevar 'A/S' y
     * 'P50' a la vez, y antes solo cabia uno.
     */
    private List<SectioningDTO> sectionings;
    /** Columna {@code Sectioning Feeding} del origen; usa el catálogo DisconnectorFunction. */
    /**
     * Aparatos de seccionamiento y alimentacion: <b>varios</b>. 'Disc SECT-I' es un
     * disconnector mas un aislador de seccion. Usa el catalogo DisconnectorFunction.
     */
    private List<DisconnectorFunctionDTO> sectioningFeedings;
}
