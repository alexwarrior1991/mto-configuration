package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El aislador de seccion y sus agujas: la estacion y las dos vias viajan como id en las dos
 * direcciones de lectura, la de las listas ({@code toDTO}) y la del detalle
 * ({@code updateDTOFromEntity}, ver {@code BaseMapper}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SectionInsulatorMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private SectionInsulatorMapper mapper;
    private SectionInsulatorSwitchMapper switchMapper;

    @BeforeEach
    void setUp() {
        MapperGraph graph = new MapperGraph(masterDataService, referenceMapper);
        mapper = graph.sectionInsulator;
        switchMapper = graph.sectionInsulatorSwitch;
    }

    private static Track track(Long id, String name) {
        Track track = new Track();
        track.setId(id);
        track.setName(name);
        track.setEnabled(true);
        return track;
    }

    private static SectionInsulator aislador() {
        Station station = new Station();
        station.setId(12L);
        SectionInsulator entity = new SectionInsulator();
        entity.setId(50L);
        entity.setName("AIS-50");
        entity.setKp(new BigDecimal("17858.000"));
        entity.setStation(station);
        entity.setTrack(track(3L, "VIA 1"));
        entity.setConnectedTrack(track(4L, "VIA 2"));
        SectionInsulatorSwitch aguja = new SectionInsulatorSwitch();
        aguja.setId(60L);
        aguja.setCode("W31");
        aguja.setKp(new BigDecimal("17860.000"));
        aguja.setTurnoutDenominator(9);
        aguja.setTrack(entity.getTrack());
        aguja.setSectionInsulator(entity);
        entity.setSwitches(new ArrayList<>(List.of(aguja)));
        return entity;
    }

    @Test
    @DisplayName("de entidad a DTO la estacion y las dos vias viajan como id, y la aguja con la suya")
    void referenciasComoId() {
        SectionInsulatorDTO dto = mapper.toDTO(aislador());

        assertThat(dto.getId()).isEqualTo(50L);
        assertThat(dto.getStationId()).isEqualTo(12L);
        assertThat(dto.getTrackId()).isEqualTo(3L);
        assertThat(dto.getConnectedTrackId()).isEqualTo(4L);
        assertThat(dto.getSwitches()).singleElement()
                .satisfies(aguja -> assertThat(aguja.getTrackId()).isEqualTo(3L));
    }

    @Test
    @DisplayName("el detalle de un aislador lleva su estacion y sus dos vias, como las listas")
    void referenciasComoIdEnElDetalle() {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        mapper.updateDTOFromEntity(aislador(), dto);

        assertThat(dto.getId()).isEqualTo(50L);
        assertThat(dto.getStationId()).isEqualTo(12L);
        assertThat(dto.getTrackId()).isEqualTo(3L);
        assertThat(dto.getConnectedTrackId()).isEqualTo(4L);
        assertThat(dto.getSwitches()).singleElement()
                .satisfies(aguja -> assertThat(aguja.getTrackId()).isEqualTo(3L));
    }

    @Test
    @DisplayName("el detalle de una aguja lleva el id de su via, como las listas")
    void viaDeLaAgujaEnElDetalle() {
        SectionInsulatorSwitchDTO dto = new SectionInsulatorSwitchDTO();
        switchMapper.updateDTOFromEntity(aislador().getSwitches().getFirst(), dto);

        assertThat(dto.getId()).isEqualTo(60L);
        assertThat(dto.getCode()).isEqualTo("W31");
        assertThat(dto.getTrackId()).isEqualTo(3L);
    }
}
