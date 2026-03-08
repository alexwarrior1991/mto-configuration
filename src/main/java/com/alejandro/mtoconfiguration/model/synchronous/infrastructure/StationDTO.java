package com.alejandro.mtoconfiguration.model.synchronous.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class StationDTO extends BaseDTO {

    private String name;
    private Long executionPackageId;
    private List<TrackDTO> tracks = new ArrayList<>();
    private List<DisconnectorDTO> disconnectors = new ArrayList<>();
    private List<SectionInsulatorDTO> sectionInsulators = new ArrayList<>();
}
