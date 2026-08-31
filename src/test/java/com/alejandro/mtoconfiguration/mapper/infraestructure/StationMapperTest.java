package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliacion de las tres colecciones de una estacion.
 *
 * <p>{@code Station} es el peor caso del proyecto: tres colecciones hijas a la vez, y ademas son
 * {@code Set} y no {@code List}, asi que la identidad de los hijos entra en juego dos veces —al
 * fusionar y al meterlos en el conjunto—. Lo que se fija aqui es que una sola peticion pueda
 * conservar, editar, añadir y borrar hijos sin duplicar ninguna fila.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StationMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private StationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).station;
    }

    private static Station estacionConDosVias() {
        Station station = new Station();
        station.setId(4L);
        station.setName("ATOCHA");
        station.setTracks(new LinkedHashSet<>(List.of(track(10L, "VIA 1"), track(11L, "VIA 2"))));
        station.setDisconnectors(new LinkedHashSet<>());
        station.setSectionInsulators(new LinkedHashSet<>());
        return station;
    }

    private static Track track(Long id, String name) {
        Track track = new Track();
        track.setId(id);
        track.setName(name);
        track.setEnabled(true);
        return track;
    }

    private static TrackDTO trackDto(Long id, String name) {
        TrackDTO dto = new TrackDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setEnabled(true);
        return dto;
    }

    private static StationDTO dto() {
        StationDTO dto = new StationDTO();
        dto.setName("ATOCHA");
        return dto;
    }

    @Nested
    @DisplayName("Vias")
    class Vias {

        @Test
        @DisplayName("una via que llega con id ACTUALIZA su fila en vez de duplicarla")
        void viaEnviadaSeActualiza() {
            Station station = estacionConDosVias();
            Track existente = station.getTracks().iterator().next();

            StationDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA PRINCIPAL"), trackDto(11L, "VIA 2"))));

            mapper.updateEntityFromDTO(dto, station);

            assertThat(station.getTracks()).hasSize(2);
            assertThat(station.getTracks()).contains(existente);
            assertThat(existente.getName()).isEqualTo("VIA PRINCIPAL");
        }

        @Test
        @DisplayName("modificar varias veces seguidas no acumula vias")
        void modificacionesRepetidasNoAcumulan() {
            Station station = estacionConDosVias();

            for (int i = 0; i < 3; i++) {
                StationDTO dto = dto();
                dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA " + i), trackDto(11L, "VIA 2"))));
                mapper.updateEntityFromDTO(dto, station);
            }

            assertThat(station.getTracks()).hasSize(2);
        }

        @Test
        @DisplayName("una misma peticion conserva, edita, añade y borra a la vez")
        void reconciliacionCompleta() {
            Station station = estacionConDosVias();

            StationDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(
                    trackDto(10L, "VIA PRINCIPAL"),   // editada
                    trackDto(null, "VIA 3"))));       // nueva; la 11 no viene, se retira

            mapper.updateEntityFromDTO(dto, station);

            assertThat(station.getTracks())
                    .extracting(t -> t.getId())
                    .containsExactlyInAnyOrder(10L, null);
            assertThat(station.getTracks())
                    .extracting(Track::getName)
                    .containsExactlyInAnyOrder("VIA PRINCIPAL", "VIA 3");
        }

        @Test
        @DisplayName("cada via queda apuntando a su estacion")
        void vinculoInverso() {
            Station station = estacionConDosVias();

            StationDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA 1"), trackDto(null, "VIA 3"))));

            mapper.updateEntityFromDTO(dto, station);

            assertThat(station.getTracks())
                    .allSatisfy(track -> assertThat(track.getStation()).isSameAs(station));
        }

        @Test
        @DisplayName("una lista de vias vacia retira todas las existentes")
        void listaVacia() {
            Station station = estacionConDosVias();

            StationDTO dto = dto();
            dto.setTracks(new ArrayList<>());

            mapper.updateEntityFromDTO(dto, station);

            assertThat(station.getTracks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Las tres colecciones a la vez")
    class TresColecciones {

        @Test
        @DisplayName("vias, seccionadores y aisladores se reconcilian en la misma peticion")
        void todasALaVez() {
            Station station = estacionConDosVias();
            Disconnector seccionador = new Disconnector();
            seccionador.setId(20L);
            seccionador.setName("SECC-1");
            seccionador.setOnLoad(true);
            station.setDisconnectors(new LinkedHashSet<>(List.of(seccionador)));

            StationDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA PRINCIPAL"))));

            DisconnectorDTO seccionadorDto = new DisconnectorDTO();
            seccionadorDto.setId(20L);
            seccionadorDto.setName("SECC-RENOMBRADO");
            seccionadorDto.setOnLoad(true);
            dto.setDisconnectors(new ArrayList<>(List.of(seccionadorDto)));

            SectionInsulatorDTO aisladorDto = new SectionInsulatorDTO();
            aisladorDto.setName("AISL-1");
            aisladorDto.setEnabled(true);
            dto.setSectionInsulators(new ArrayList<>(List.of(aisladorDto)));

            mapper.updateEntityFromDTO(dto, station);

            assertThat(station.getTracks()).hasSize(1);
            assertThat(station.getDisconnectors()).hasSize(1);
            assertThat(seccionador.getName())
                    .as("el seccionador existente se actualiza, no se duplica")
                    .isEqualTo("SECC-RENOMBRADO");
            assertThat(station.getSectionInsulators())
                    .extracting(SectionInsulator::getName)
                    .containsExactly("AISL-1");
            assertThat(station.getSectionInsulators())
                    .allSatisfy(a -> assertThat(a.getStation()).isSameAs(station));
        }
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("un alta con hijos nuevos los crea todos, sin duplicar ninguno")
        void altaConHijos() {
            // La misma reconciliacion sirve para el alta: la coleccion parte vacia y todos los
            // DTO llegan sin id.
            StationDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(null, "VIA 1"), trackDto(null, "VIA 2"))));

            Station station = mapper.toEntity(dto);

            assertThat(station.getTracks())
                    .extracting(Track::getName)
                    .containsExactlyInAnyOrder("VIA 1", "VIA 2");
            assertThat(station.getTracks())
                    .allSatisfy(track -> assertThat(track.getStation()).isSameAs(station));
        }
    }
}
