package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.enums.infrastructure.DisconnectorDriveType;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El seccionador sale con su perfil legible.
 *
 * <p>Una lista de seccionadores solo traia {@code profileId}, y quien la mostraba tenia que ir
 * perfil por perfil para enseñar algo que una persona reconozca. {@code profileCode} y
 * {@code profileKp} son de solo salida: al escribir, el perfil se elige por {@code profileId}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisconnectorMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private DisconnectorMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).disconnector;
    }

    private static Disconnector seccionador() {
        Station station = new Station();
        station.setId(3L);
        Profile profile = new Profile();
        profile.setId(7L);
        profile.setProfileId("P-007");
        profile.setKp(new BigDecimal("12.345"));
        Disconnector entity = new Disconnector();
        entity.setId(1L);
        entity.setName("SEC-1");
        entity.setOnLoad(true);
        entity.setStation(station);
        entity.setProfile(profile);
        return entity;
    }

    @Test
    @DisplayName("la salida lleva el id, el identificador y el KP del perfil")
    void elPerfilSaleLegible() {
        DisconnectorDTO dto = mapper.toDTO(seccionador());

        assertThat(dto.getStationId()).isEqualTo(3L);
        assertThat(dto.getProfileId()).isEqualTo(7L);
        assertThat(dto.getProfileCode()).isEqualTo("P-007");
        assertThat(dto.getProfileKp()).isEqualTo("12.345");
        assertThat(dto.getOnLoad()).isTrue();
    }

    @Test
    @DisplayName("sin perfil, los tres campos del perfil van a null")
    void sinPerfilNoHayNadaQueEnseñar() {
        Disconnector entity = seccionador();
        entity.setProfile(null);

        DisconnectorDTO dto = mapper.toDTO(entity);

        assertThat(dto.getProfileId()).isNull();
        assertThat(dto.getProfileCode()).isNull();
        assertThat(dto.getProfileKp()).isNull();
    }

    @Test
    @DisplayName("al escribir, el identificador y el KP del perfil no pintan nada")
    void alEscribirSeIgnoran() {
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName("SEC-1");
        dto.setOnLoad(false);
        dto.setProfileCode("otro perfil");
        dto.setProfileKp("99.999");

        Disconnector entity = mapper.toEntity(dto);

        assertThat(entity.getName()).isEqualTo("SEC-1");
        assertThat(entity.getProfile()).isNull();

        Disconnector existente = seccionador();
        mapper.updateEntityFromDTO(dto, existente);
        assertThat(existente.getProfile()).isNull();
        assertThat(existente.getName()).isEqualTo("SEC-1");
    }

    @Test
    @DisplayName("el detalle de un seccionador lleva su estacion y su perfil legible, como las listas")
    void estacionYPerfilEnElDetalle() {
        DisconnectorDTO dto = new DisconnectorDTO();
        mapper.updateDTOFromEntity(seccionador(), dto);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getStationId()).isEqualTo(3L);
        assertThat(dto.getProfileId()).isEqualTo(7L);
        assertThat(dto.getProfileCode()).isEqualTo("P-007");
        assertThat(dto.getProfileKp()).isEqualTo("12.345");
    }

    @Test
    @DisplayName("el estado normal y el accionamiento van y vuelven, y un PUT sin ellos los vacia")
    void estadoNormalYAccionamiento() {
        Disconnector entity = seccionador();
        entity.setNormallyOpen(true);
        entity.setDriveType(DisconnectorDriveType.MOTOR);

        DisconnectorDTO dto = mapper.toDTO(entity);
        assertThat(dto.getNormallyOpen()).isTrue();
        assertThat(dto.getDriveType()).isEqualTo(DisconnectorDriveType.MOTOR);

        dto.setNormallyOpen(false);
        dto.setDriveType(DisconnectorDriveType.MANUAL);
        Disconnector creado = mapper.toEntity(dto);
        assertThat(creado.getNormallyOpen()).isFalse();
        assertThat(creado.getDriveType()).isEqualTo(DisconnectorDriveType.MANUAL);

        // El PUT sustituye la fila entera (README_API.md §4): lo que no viaja se queda sin dato.
        DisconnectorDTO sinDatos = new DisconnectorDTO();
        sinDatos.setName("SEC-1");
        sinDatos.setOnLoad(true);
        mapper.updateEntityFromDTO(sinDatos, entity);
        assertThat(entity.getNormallyOpen()).isNull();
        assertThat(entity.getDriveType()).isNull();
    }

    @Test
    @DisplayName("un seccionador sin poste lleva su KP como texto y su via por id, en la salida y en el detalle")
    void kpYViaDeUnSeccionadorSinPoste() {
        Track track = new Track();
        track.setId(3L);
        Disconnector entity = seccionador();
        entity.setProfile(null);
        entity.setKp(new BigDecimal("98375.500"));
        entity.setTrack(track);

        DisconnectorDTO dto = mapper.toDTO(entity);
        assertThat(dto.getKp()).isEqualTo("98375.500");
        assertThat(dto.getTrackId()).isEqualTo(3L);
        DisconnectorDTO detalle = new DisconnectorDTO();
        mapper.updateDTOFromEntity(entity, detalle);
        assertThat(detalle.getTrackId()).isEqualTo(3L);

        dto.setKp("98400.5");
        Disconnector escrito = mapper.toEntity(dto);
        assertThat(escrito.getKp()).isEqualByComparingTo("98400.5");
    }

    @Test
    @DisplayName("la via conectada va y vuelve por id, tambien con poste, y un PUT sin ella la quita")
    void viaConectada() {
        Track connected = new Track();
        connected.setId(4L);
        Disconnector entity = seccionador();
        entity.setConnectedTrack(connected);

        DisconnectorDTO dto = mapper.toDTO(entity);
        assertThat(dto.getProfileId()).isEqualTo(7L);
        assertThat(dto.getConnectedTrackId()).isEqualTo(4L);
        DisconnectorDTO detalle = new DisconnectorDTO();
        mapper.updateDTOFromEntity(entity, detalle);
        assertThat(detalle.getConnectedTrackId()).isEqualTo(4L);

        when(referenceMapper.resolve(4L, Track.class)).thenReturn(connected);
        assertThat(mapper.toEntity(dto).getConnectedTrack()).isSameAs(connected);

        // El PUT sustituye la fila entera (README_API.md §4): sin via conectada, deja de tenerla.
        DisconnectorDTO sinConectada = new DisconnectorDTO();
        sinConectada.setName("SEC-1");
        sinConectada.setOnLoad(true);
        mapper.updateEntityFromDTO(sinConectada, entity);
        assertThat(entity.getConnectedTrack()).isNull();
    }

    /**
     * Con poste, la via del seccionador es la del perfil, que el DTO no trae: la compara el mapper,
     * que es el paso de todas las escrituras (tambien el alta en lote y las anidadas en su estacion).
     */
    @Test
    @DisplayName("con poste, la via conectada no puede ser la del perfil, ni al dar de alta ni al modificar")
    void laViaConectadaNoEsLaDelPoste() {
        Track own = new Track();
        own.setId(3L);
        Track other = new Track();
        other.setId(4L);
        Profile pole = new Profile();
        pole.setId(7L);
        pole.setTrack(own);
        when(referenceMapper.resolve(7L, Profile.class)).thenReturn(pole);
        when(referenceMapper.resolve(3L, Track.class)).thenReturn(own);
        when(referenceMapper.resolve(4L, Track.class)).thenReturn(other);
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName("SEC-B01");
        dto.setOnLoad(false);
        dto.setProfileId(7L);
        dto.setConnectedTrackId(3L);

        assertThatThrownBy(() -> mapper.toEntity(dto))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors())
                        .singleElement()
                        .satisfies(alert -> {
                            assertThat(alert.getMessage()).isEqualTo(ErrorCodes.BUSINESS_RULE_VIOLATION);
                            assertThat(alert.getFields()).containsExactly("connectedTrackId");
                        }));
        assertThatThrownBy(() -> mapper.updateEntityFromDTO(dto, seccionador()))
                .isInstanceOf(ValidationException.class);

        dto.setConnectedTrackId(4L);
        assertThat(mapper.toEntity(dto).getConnectedTrack()).isSameAs(other);
    }

    @Test
    @DisplayName("sin via conectada no se lee la via del poste: el perfil no se carga por ella")
    void sinViaConectadaNoSeCargaElPerfil() {
        Profile pole = mock(Profile.class);
        when(referenceMapper.resolve(7L, Profile.class)).thenReturn(pole);
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName("SEC-1");
        dto.setOnLoad(true);
        dto.setProfileId(7L);

        assertThat(mapper.toEntity(dto).getProfile()).isSameAs(pole);
        verify(pole, never()).getTrack();
    }
}
