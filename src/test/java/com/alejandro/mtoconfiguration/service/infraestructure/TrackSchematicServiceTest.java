package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.CantileverArm;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.InsulatorMark;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic.TrackSchematicDTO.ProfileNode;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.CantileverRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository.ProfileSectioningCode;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el esquema de una via saca de las cinco consultas, y como se cachea.
 *
 * <p>El orden de los perfiles es el que da el repositorio (el fisico, {@code orderInTrack}): el
 * servicio no reordena. Las ménsulas y los seccionamientos llegan en consultas aparte y se agrupan
 * aqui por id de perfil; un perfil sin ellos lleva listas vacias, no {@code null}, y todas las listas
 * son {@code ArrayList} porque van a Redis ({@code RedisCacheValueSerializationTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackSchematicServiceTest {

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private CantileverRepository cantileverRepository;
    @Mock
    private SectionInsulatorRepository sectionInsulatorRepository;

    private TrackSchematicService service;

    private Track via1;
    private Track via2;
    private Station atocha;
    private Profile p1;
    private Profile p2;

    /** La proyeccion de seccionamientos, como la devuelve Spring Data. */
    private record Seccionamiento(Long profileId, String code) implements ProfileSectioningCode {
        @Override
        public Long getProfileId() {
            return profileId;
        }

        @Override
        public String getCode() {
            return code;
        }
    }

    private static <T extends Lov> T lov(T lov, String code) {
        lov.setCode(code);
        lov.setDescription("Descripcion de " + code);
        return lov;
    }

    private static Station station(Long id, String name) {
        Station station = new Station();
        station.setId(id);
        station.setName(name);
        return station;
    }

    private static Track track(Long id, String name) {
        Track track = new Track();
        track.setId(id);
        track.setName(name);
        track.setEnabled(true);
        return track;
    }

    private static Profile profile(Long id, String code, String kp, Integer orderInTrack, Track track) {
        Profile profile = new Profile();
        profile.setId(id);
        profile.setProfileId(code);
        profile.setKp(new BigDecimal(kp));
        profile.setOrderInTrack(orderInTrack);
        profile.setTrack(track);
        return profile;
    }

    private static Cantilever cantilever(Long id, Profile profile, CantileverType type, String stagger) {
        Cantilever cantilever = new Cantilever();
        cantilever.setId(id);
        cantilever.setProfile(profile);
        cantilever.setCantileverType(type);
        cantilever.setStagger(new BigDecimal(stagger));
        cantilever.setCwHeight(new BigDecimal("5.300"));
        cantilever.setCatenaryHeight(new BigDecimal("1.400"));
        return cantilever;
    }

    @BeforeEach
    void setUp() {
        service = new TrackSchematicService(trackRepository, profileRepository, cantileverRepository,
                sectionInsulatorRepository);

        ExecutionPackage paquete = new ExecutionPackage();
        paquete.setId(100L);
        paquete.setName("EP4");
        atocha = station(13L, "ATOCHA");
        via1 = track(3L, "VIA 1");
        via1.setExecutionPackage(paquete);
        via1.setStations(new LinkedHashSet<>(List.of(station(12L, "CHAMARTIN"), atocha)));
        via2 = track(4L, "VIA 2");

        p1 = profile(1L, "P-001", "10.000", 1, via1);
        p1.setPoleType(lov(new PoleType(), "HEB"));
        p1.setProfileStatus(lov(new ProfileStatus(), "OK"));
        p1.setSpan(new BigDecimal("55.000"));
        p1.setRailPoleDistance(new BigDecimal("-2500"));

        p2 = profile(2L, "P-002", "20.000", 2, via1);
        Disconnector seccionador = new Disconnector();
        seccionador.setId(40L);
        seccionador.setName("SEC-40");
        seccionador.setOnLoad(true);
        seccionador.setDisconnectorFunction(lov(new DisconnectorFunction(), "FEED"));
        seccionador.setStation(atocha);
        p2.addDisconnector(seccionador);

        Cantilever c21 = cantilever(21L, p1, lov(new CantileverType(), "PT1"), "-200");
        SteadyArm brazo = new SteadyArm();
        brazo.setId(31L);
        brazo.setLength(1200L);
        brazo.setSteadyArmType(lov(new SteadyArmType(), "SA1"));
        c21.addSteadyArm(brazo);
        Cantilever c22 = cantilever(22L, p1, null, "200");

        SectionInsulator aislador = new SectionInsulator();
        aislador.setId(50L);
        aislador.setName("AIS-50");
        aislador.setKp(new BigDecimal("15.000"));
        aislador.setInstallationType(SectionInsulatorInstallationType.TRACK_CONNECTION);
        aislador.setEnabled(true);
        aislador.setStation(atocha);
        aislador.setTrack(via1);
        aislador.setConnectedTrack(via2);
        SectionInsulatorSwitch aguja = new SectionInsulatorSwitch();
        aguja.setId(60L);
        aguja.setCode("W31");
        aguja.setKp(new BigDecimal("15.500"));
        aguja.setTurnoutDenominator(9);
        aguja.setTrack(via1);
        aislador.addSwitch(aguja);

        when(trackRepository.findForSchematic(3L)).thenReturn(Optional.of(via1));
        when(profileRepository.findForSchematic(3L)).thenReturn(List.of(p1, p2));
        when(cantileverRepository.findForSchematic(3L)).thenReturn(List.of(c21, c22));
        when(profileRepository.findSectioningCodesForSchematic(3L)).thenReturn(List.of(new Seccionamiento(1L, "S1")));
        when(sectionInsulatorRepository.findForSchematic(3L)).thenReturn(List.of(aislador));
    }

    @Nested
    @DisplayName("Lo que lleva el esquema")
    class Contenido {

        @Test
        @DisplayName("la cabecera: la via, su paquete y sus estaciones ordenadas por nombre")
        void cabecera() {
            TrackSchematicDTO esquema = service.getSchematic(3L);

            assertThat(esquema.trackId()).isEqualTo(3L);
            assertThat(esquema.trackName()).isEqualTo("VIA 1");
            assertThat(esquema.enabled()).isTrue();
            assertThat(esquema.executionPackageName()).isEqualTo("EP4");
            assertThat(esquema.stations()).containsExactly("ATOCHA", "CHAMARTIN");
        }

        @Test
        @DisplayName("los perfiles salen en el orden del repositorio, con sus medidas como texto plano")
        void perfilesEnSuOrden() {
            List<ProfileNode> perfiles = service.getSchematic(3L).profiles();

            assertThat(perfiles).extracting(ProfileNode::code).containsExactly("P-001", "P-002");
            ProfileNode primero = perfiles.getFirst();
            assertThat(primero.id()).isEqualTo(1L);
            assertThat(primero.kp()).isEqualTo("10.000");
            assertThat(primero.orderInTrack()).isEqualTo(1);
            assertThat(primero.span()).isEqualTo("55.000");
            assertThat(primero.railPoleDistance()).isEqualTo("-2500");
            assertThat(primero.poleType()).isEqualTo("HEB");
            assertThat(primero.profileStatus()).isEqualTo("OK");
            assertThat(primero.supportType()).as("lo que no hay va a null, no a texto vacio").isNull();
            assertThat(primero.sectionings()).containsExactly("S1");
        }

        @Test
        @DisplayName("las ménsulas se agrupan por perfil, con su tipo y su brazo")
        void mensulasPorPerfil() {
            List<ProfileNode> perfiles = service.getSchematic(3L).profiles();

            assertThat(perfiles.getFirst().cantilevers())
                    .extracting(CantileverArm::id, CantileverArm::type, CantileverArm::stagger,
                            CantileverArm::steadyArmType, CantileverArm::steadyArmLength)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(21L, "PT1", "-200", "SA1", 1200L),
                            org.assertj.core.groups.Tuple.tuple(22L, null, "200", null, null));
            assertThat(perfiles.getFirst().cantilevers().getFirst().cwHeight()).isEqualTo("5.300");
            assertThat(perfiles.get(1).cantilevers()).as("sin ménsulas: lista vacia, no null").isEmpty();
            assertThat(perfiles.get(1).sectionings()).isEmpty();
        }

        @Test
        @DisplayName("el seccionador cuelga de su perfil con su funcion y su estacion")
        void seccionadorSobreSuPerfil() {
            List<ProfileNode> perfiles = service.getSchematic(3L).profiles();

            assertThat(perfiles.getFirst().disconnector()).isNull();
            assertThat(perfiles.get(1).disconnector().id()).isEqualTo(40L);
            assertThat(perfiles.get(1).disconnector().name()).isEqualTo("SEC-40");
            assertThat(perfiles.get(1).disconnector().onLoad()).isTrue();
            assertThat(perfiles.get(1).disconnector().function()).isEqualTo("FEED");
            assertThat(perfiles.get(1).disconnector().station()).isEqualTo("ATOCHA");
        }

        @Test
        @DisplayName("los aisladores llevan su KP, sus dos vias por nombre, su estacion y sus agujas")
        void aisladores() {
            List<InsulatorMark> aisladores = service.getSchematic(3L).sectionInsulators();

            assertThat(aisladores).hasSize(1);
            InsulatorMark marca = aisladores.getFirst();
            assertThat(marca.id()).isEqualTo(50L);
            assertThat(marca.name()).isEqualTo("AIS-50");
            assertThat(marca.kp()).isEqualTo("15.000");
            assertThat(marca.installationType()).isEqualTo("TRACK_CONNECTION");
            assertThat(marca.enabled()).isTrue();
            assertThat(marca.station()).isEqualTo("ATOCHA");
            assertThat(marca.track()).isEqualTo("VIA 1");
            assertThat(marca.connectedTrack()).isEqualTo("VIA 2");
            assertThat(marca.switches()).singleElement().satisfies(aguja -> {
                assertThat(aguja.id()).isEqualTo(60L);
                assertThat(aguja.code()).isEqualTo("W31");
                assertThat(aguja.kp()).isEqualTo("15.500");
                assertThat(aguja.turnoutDenominator()).isEqualTo(9);
                assertThat(aguja.track()).isEqualTo("VIA 1");
            });
        }

        @Test
        @DisplayName("todas las listas son ArrayList: van a Redis y las inmutables no se releen")
        void listasMutables() {
            TrackSchematicDTO esquema = service.getSchematic(3L);

            assertThat(esquema.stations()).isInstanceOf(ArrayList.class);
            assertThat(esquema.profiles()).isInstanceOf(ArrayList.class);
            assertThat(esquema.sectionInsulators()).isInstanceOf(ArrayList.class);
            assertThat(esquema.profiles().getFirst().cantilevers()).isInstanceOf(ArrayList.class);
            assertThat(esquema.profiles().getFirst().sectionings()).isInstanceOf(ArrayList.class);
            assertThat(esquema.profiles().get(1).cantilevers()).isInstanceOf(ArrayList.class);
            assertThat(esquema.profiles().get(1).sectionings()).isInstanceOf(ArrayList.class);
            assertThat(esquema.sectionInsulators().getFirst().switches()).isInstanceOf(ArrayList.class);
        }

        @Test
        @DisplayName("una via sin perfiles ni aisladores es un esquema vacio, no un error")
        void viaVacia() {
            when(profileRepository.findForSchematic(3L)).thenReturn(List.of());
            when(cantileverRepository.findForSchematic(3L)).thenReturn(List.of());
            when(profileRepository.findSectioningCodesForSchematic(3L)).thenReturn(List.of());
            when(sectionInsulatorRepository.findForSchematic(3L)).thenReturn(List.of());

            TrackSchematicDTO esquema = service.getSchematic(3L);

            assertThat(esquema.profiles()).isEmpty();
            assertThat(esquema.sectionInsulators()).isEmpty();
        }

        @Test
        @DisplayName("una via inexistente es un 404, y no se consulta nada mas")
        void viaInexistente() {
            when(trackRepository.findForSchematic(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSchematic(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("99");
            verify(profileRepository, org.mockito.Mockito.never()).findForSchematic(99L);
        }
    }

    @Nested
    @DisplayName("Cache")
    class Cache {

        @Test
        @DisplayName("se cachea como elemento normal por el generador de claves, y lee en transaccion")
        void anotaciones() throws NoSuchMethodException {
            Method metodo = TrackSchematicService.class.getMethod("getSchematic", Long.class);

            Cacheable cacheable = metodo.getAnnotation(Cacheable.class);
            assertThat(cacheable.cacheNames()).containsExactly(CacheNames.NORMAL_ITEM);
            assertThat(cacheable.keyGenerator()).isEqualTo("redisCacheKeyGenerator");
            assertThat(cacheable.condition()).as("sin #root.target.cacheable: aqui siempre").isEmpty();

            Transactional transactional = metodo.getAnnotation(Transactional.class);
            assertThat(transactional.readOnly()).isTrue();
        }

        @Test
        @DisplayName("la clave que genera es la que barre evictTrackSchematics")
        void laClaveCasaConLaInvalidacion() {
            RedisCacheKeyGenerator generador =
                    new RedisCacheKeyGenerator(new RedisCacheConfig().redisCacheKeyObjectMapper());

            assertThat(generador.buildKey(service, "getSchematic", 3L))
                    .isEqualTo(RedisCacheEvictService.TRACK_SCHEMATIC_KEY_PREFIX + "getSchematic:3");
        }
    }
}
