package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliacion de las dos colecciones de un paquete de ejecucion.
 *
 * <p>Es la raiz del arbol de infraestructura: por debajo cuelgan vias, estaciones, perfiles y
 * mensulas. Duplicar aqui duplica ramas enteras, asi que es el sitio donde el fallo salia mas
 * caro y donde la reconciliacion tiene que resistir la modificacion anidada en varios niveles.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionPackageMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private ExecutionPackageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).executionPackage;
    }

    private static ExecutionPackageDTO dto() {
        ExecutionPackageDTO dto = new ExecutionPackageDTO();
        dto.setName("PAQUETE NORTE");
        dto.setInitialPackage(false);
        dto.setLength(1000L);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(LocalDate.of(2026, 12, 31));
        dto.setEnabled(true);
        return dto;
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

    private static Station station(Long id, String name) {
        Station station = new Station();
        station.setId(id);
        station.setName(name);
        station.setTracks(new LinkedHashSet<>());
        station.setDisconnectors(new LinkedHashSet<>());
        station.setSectionInsulators(new LinkedHashSet<>());
        return station;
    }

    private static StationDTO stationDto(Long id, String name) {
        StationDTO dto = new StationDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static ExecutionPackage paquete() {
        ExecutionPackage paquete = new ExecutionPackage();
        paquete.setId(100L);
        paquete.setName("PAQUETE NORTE");
        paquete.setInitialPackage(false);
        paquete.setLength(1000L);
        paquete.setStartDate(LocalDate.of(2026, 1, 1));
        paquete.setEndDate(LocalDate.of(2026, 12, 31));
        paquete.setEnabled(true);
        paquete.setTracks(new LinkedHashSet<>(List.of(track(10L, "VIA 1"), track(11L, "VIA 2"))));
        paquete.setStations(new LinkedHashSet<>(List.of(station(20L, "ATOCHA"))));
        return paquete;
    }

    @Nested
    @DisplayName("Campos propios")
    class CamposPropios {

        @Test
        @DisplayName("los campos del paquete viajan en los dos sentidos")
        void idaYVuelta() {
            ExecutionPackage entity = mapper.toEntity(dto());

            assertThat(entity.getName()).isEqualTo("PAQUETE NORTE");
            assertThat(entity.getLength()).isEqualTo(1000L);
            assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        @DisplayName("las propiedades de auditoria del DTO no se copian a la entidad")
        void auditoriaIgnorada() {
            ExecutionPackageDTO dto = dto();
            dto.setCreateUser("intruso");
            dto.setCreateDate(LocalDateTime.of(2000, 1, 1, 0, 0));
            dto.setVersionNumber(99);

            ExecutionPackage entity = mapper.toEntity(dto);

            assertThat(entity.getCreateUser()).isNull();
            assertThat(entity.getCreateDate()).isNull();
            assertThat(entity.getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("un nulo se mapea a nulo en los dos sentidos")
        void nulos() {
            assertThat(mapper.toEntity(null)).isNull();
            assertThat(mapper.toDTO(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Reconciliacion de vias y estaciones")
    class Reconciliacion {

        @Test
        @DisplayName("una via que llega con id ACTUALIZA su fila en vez de duplicarla")
        void viaEnviadaSeActualiza() {
            ExecutionPackage entity = paquete();
            Track existente = entity.getTracks().iterator().next();

            ExecutionPackageDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA PRINCIPAL"), trackDto(11L, "VIA 2"))));
            dto.setStations(new ArrayList<>(List.of(stationDto(20L, "ATOCHA"))));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getTracks()).hasSize(2);
            assertThat(entity.getTracks()).contains(existente);
            assertThat(existente.getName()).isEqualTo("VIA PRINCIPAL");
        }

        @Test
        @DisplayName("modificar varias veces seguidas no acumula ni vias ni estaciones")
        void modificacionesRepetidasNoAcumulan() {
            ExecutionPackage entity = paquete();

            for (int i = 0; i < 3; i++) {
                ExecutionPackageDTO dto = dto();
                dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA " + i), trackDto(11L, "VIA 2"))));
                dto.setStations(new ArrayList<>(List.of(stationDto(20L, "ATOCHA"))));
                mapper.updateEntityFromDTO(dto, entity);
            }

            assertThat(entity.getTracks()).hasSize(2);
            assertThat(entity.getStations()).hasSize(1);
        }

        @Test
        @DisplayName("una misma peticion conserva, edita, añade y borra en las dos colecciones")
        void reconciliacionCompleta() {
            ExecutionPackage entity = paquete();

            ExecutionPackageDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(
                    trackDto(10L, "VIA PRINCIPAL"),   // editada; la 11 no viene, se retira
                    trackDto(null, "VIA 3"))));       // nueva
            dto.setStations(new ArrayList<>(List.of(
                    stationDto(20L, "ATOCHA RENOMBRADA"),
                    stationDto(null, "CHAMARTIN"))));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getTracks())
                    .extracting(Track::getName)
                    .containsExactlyInAnyOrder("VIA PRINCIPAL", "VIA 3");
            assertThat(entity.getStations())
                    .extracting(Station::getName)
                    .containsExactlyInAnyOrder("ATOCHA RENOMBRADA", "CHAMARTIN");
        }

        @Test
        @DisplayName("cada hijo queda apuntando a su paquete")
        void vinculoInverso() {
            ExecutionPackage entity = paquete();

            ExecutionPackageDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(10L, "VIA 1"), trackDto(null, "VIA 3"))));
            dto.setStations(new ArrayList<>(List.of(stationDto(null, "CHAMARTIN"))));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getTracks())
                    .allSatisfy(t -> assertThat(t.getExecutionPackage()).isSameAs(entity));
            assertThat(entity.getStations())
                    .allSatisfy(s -> assertThat(s.getExecutionPackage()).isSameAs(entity));
        }

        @Test
        @DisplayName("la modificacion anidada llega hasta los perfiles de una via")
        void modificacionAnidada() {
            // Tres niveles: paquete -> via -> perfil. La reconciliacion de cada nivel llama a la
            // del siguiente, asi que basta con que uno la haga mal para duplicar todo el subarbol.
            ExecutionPackage entity = paquete();
            Track via = entity.getTracks().iterator().next();
            com.alejandro.mtoconfiguration.entity.infrastructure.Profile perfil =
                    new com.alejandro.mtoconfiguration.entity.infrastructure.Profile();
            perfil.setId(30L);
            perfil.setProfileId("P-001");
            perfil.setKp(new java.math.BigDecimal("10.000"));
            via.setProfiles(new ArrayList<>(List.of(perfil)));

            com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO perfilDto =
                    new com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO();
            perfilDto.setId(30L);
            perfilDto.setProfileId("P-001-BIS");
            perfilDto.setKp("11.000");

            TrackDTO viaDto = trackDto(via.getId(), via.getName());
            viaDto.setProfiles(new ArrayList<>(List.of(perfilDto)));

            ExecutionPackageDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(viaDto)));
            dto.setStations(new ArrayList<>(List.of(stationDto(20L, "ATOCHA"))));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(via.getProfiles()).hasSize(1);
            assertThat(perfil.getProfileId())
                    .as("el perfil se actualiza en el sitio, dos niveles por debajo del paquete")
                    .isEqualTo("P-001-BIS");
        }

        @Test
        @DisplayName("listas vacias retiran todos los hijos")
        void listasVacias() {
            ExecutionPackage entity = paquete();

            ExecutionPackageDTO dto = dto();
            dto.setTracks(new ArrayList<>());
            dto.setStations(new ArrayList<>());

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getTracks()).isEmpty();
            assertThat(entity.getStations()).isEmpty();
        }

        @Test
        @DisplayName("un alta con vias y estaciones nuevas las crea todas")
        void altaConHijos() {
            ExecutionPackageDTO dto = dto();
            dto.setTracks(new ArrayList<>(List.of(trackDto(null, "VIA 1"), trackDto(null, "VIA 2"))));
            dto.setStations(new ArrayList<>(List.of(stationDto(null, "ATOCHA"))));

            ExecutionPackage entity = mapper.toEntity(dto);

            assertThat(entity.getTracks()).extracting(Track::getName)
                    .containsExactlyInAnyOrder("VIA 1", "VIA 2");
            assertThat(entity.getStations()).extracting(Station::getName)
                    .containsExactly("ATOCHA");
        }
    }
}
