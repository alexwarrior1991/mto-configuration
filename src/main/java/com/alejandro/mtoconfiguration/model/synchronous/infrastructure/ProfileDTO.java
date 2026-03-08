package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;


import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProfileDTO extends BaseDTO {

    private String profileId;
    private String kp;

    private Long trackId;
    private DisconnectorDTO disconnector;
    private List<CantileverDTO> cantilevers = new ArrayList<>();

    private AnchorageDTO anchorage;
    private AnchorageFoundationDTO anchorageFoundation;
    private FoundationDTO foundation;
    private PoleTypeDTO poleType;
    private PortalDTO portal;
    private ProfileStatusDTO profileStatus;
    private ReturnSupportDTO returnSupport;
    private SectioningDTO sectioning;
}
