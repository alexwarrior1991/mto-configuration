package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.validator.ValidDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El punto de acceso a la vía (V28): la vía y la estación viajan como id en las dos direcciones de
 * lectura, la de las listas ({@code toDTO}) y la del detalle ({@code updateDTOFromEntity}, ver
 * {@code BaseMapper}), y al escribir se resuelven por id con {@link ReferenceMapper}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackAccessPointMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private TrackAccessPointMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).trackAccessPoint;
    }

    private static Track track(Long id) {
        Track track = new Track();
        track.setId(id);
        track.setName("VIA " + id);
        return track;
    }

    private static Station station(Long id) {
        Station station = new Station();
        station.setId(id);
        station.setName("ESTACION " + id);
        return station;
    }

    private static TrackAccessPoint acceso() {
        TrackAccessPoint entity = new TrackAccessPoint();
        entity.setId(70L);
        entity.setName("Acceso km 110+176");
        entity.setTrack(track(3L));
        entity.setStation(station(12L));
        entity.setKp(new BigDecimal("110176.000"));
        entity.setKind(TrackAccessPointKind.LEVEL_CROSSING);
        entity.setSide(TrackAccessPointSide.BOTH);
        entity.setRoadRailAccess(Boolean.FALSE);
        entity.setMaxAxleLoadT(new BigDecimal("12.500"));
        entity.setMaxGrossWeightT(new BigDecimal("40.000"));
        entity.setLatitude(new BigDecimal("31.669342"));
        entity.setLongitude(new BigDecimal("34.574119"));
        entity.setEnabled(Boolean.TRUE);
        return entity;
    }

    @Nested
    @DisplayName("De entidad a DTO")
    class Lectura {

        @Test
        @DisplayName("en las listas la vía y la estación viajan como id, con todos los escalares")
        void lista() {
            TrackAccessPointDTO dto = mapper.toDTO(acceso());

            assertThat(dto.getId()).isEqualTo(70L);
            assertThat(dto.getTrackId()).isEqualTo(3L);
            assertThat(dto.getStationId()).isEqualTo(12L);
            assertThat(dto.getKp()).isEqualByComparingTo("110176");
            assertThat(dto.getKind()).isEqualTo(TrackAccessPointKind.LEVEL_CROSSING);
            assertThat(dto.getSide()).isEqualTo(TrackAccessPointSide.BOTH);
            assertThat(dto.getRoadRailAccess()).isFalse();
            assertThat(dto.getMaxAxleLoadT()).isEqualByComparingTo("12.5");
            assertThat(dto.getMaxGrossWeightT()).isEqualByComparingTo("40");
            assertThat(dto.getLatitude()).isEqualByComparingTo("31.669342");
            assertThat(dto.getLongitude()).isEqualByComparingTo("34.574119");
            assertThat(dto.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("el detalle lleva la vía y la estación, como las listas")
        void detalle() {
            TrackAccessPointDTO dto = new TrackAccessPointDTO();
            mapper.updateDTOFromEntity(acceso(), dto);

            assertThat(dto.getId()).isEqualTo(70L);
            assertThat(dto.getTrackId()).isEqualTo(3L);
            assertThat(dto.getStationId()).isEqualTo(12L);
        }

        @Test
        @DisplayName("sin estación, stationId va a null")
        void sinEstacion() {
            TrackAccessPoint entity = acceso();
            entity.setStation(null);

            assertThat(mapper.toDTO(entity).getStationId()).isNull();
        }
    }

    @Nested
    @DisplayName("De DTO a entidad")
    class Escritura {

        @Test
        @DisplayName("el alta resuelve la vía y la estación por su id y no copia ni el id ni la auditoría")
        void alta() {
            when(referenceMapper.resolve(eq(3L), eq(Track.class))).thenReturn(track(3L));
            when(referenceMapper.resolve(eq(40L), eq(Station.class))).thenReturn(station(40L));

            TrackAccessPointDTO dto = ValidDtos.rootTrackAccessPoint();
            dto.setId(999L);
            dto.setVersionNumber(7);
            dto.setCreateUser("intruso");
            dto.setCreateDate(LocalDateTime.of(2000, 1, 1, 0, 0));

            TrackAccessPoint entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getVersionNumber()).isEqualTo(1);
            assertThat(entity.getCreateUser()).isNull();
            assertThat(entity.getCreateDate()).isNull();
            assertThat(entity.getTrack().getId()).isEqualTo(3L);
            assertThat(entity.getStation().getId()).isEqualTo(40L);
            assertThat(entity.getKind()).isEqualTo(TrackAccessPointKind.PREPARED_ACCESS);
            assertThat(entity.getSide()).isEqualTo(TrackAccessPointSide.LEFT);
            assertThat(entity.getLatitude()).isEqualByComparingTo("31.669342");
        }

        @Test
        @DisplayName("modificar sin estación la quita, como el resto de la fila (devuélvelo entero)")
        void modificarSinEstacionLaQuita() {
            when(referenceMapper.resolve(eq(3L), eq(Track.class))).thenReturn(track(3L));

            TrackAccessPoint entity = acceso();
            TrackAccessPointDTO dto = ValidDtos.rootTrackAccessPoint();
            dto.setStationId(null);
            dto.setName("Otro nombre");

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getId()).isEqualTo(70L);
            assertThat(entity.getStation()).isNull();
            assertThat(entity.getName()).isEqualTo("Otro nombre");
        }
    }
}
