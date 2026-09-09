package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Reconciliacion de los perfiles de una via.
 *
 * <p>Es la coleccion mas grande del modelo: una via de obra puede llevar cientos de perfiles, asi
 * que duplicarla en cada guardado no es una molestia, es lo que hace inservible la tabla. Ademas
 * el perfil arrastra sus propios hijos (mensulas, seccionador), de modo que una copia del perfil
 * se lleva por delante todo su subarbol.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private TrackMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).track;
    }

    private static Profile profile(Long id, String profileId, String kp) {
        Profile entity = new Profile();
        entity.setId(id);
        entity.setProfileId(profileId);
        entity.setKp(new BigDecimal(kp));
        return entity;
    }

    private static ProfileDTO profileDto(Long id, String profileId, String kp) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        dto.setProfileId(profileId);
        dto.setKp(kp);
        return dto;
    }

    private static Track viaConTresPerfiles() {
        Track track = new Track();
        track.setId(3L);
        track.setName("VIA 1");
        track.setEnabled(true);
        track.setProfiles(new ArrayList<>(List.of(
                profile(1L, "P-001", "10.000"),
                profile(2L, "P-002", "20.000"),
                profile(3L, "P-003", "30.000"))));
        return track;
    }

    private static TrackDTO dto() {
        TrackDTO dto = new TrackDTO();
        dto.setName("VIA 1");
        dto.setEnabled(true);
        return dto;
    }

    @Nested
    @DisplayName("Campos propios")
    class CamposPropios {

        @Test
        @DisplayName("los campos de la via viajan en los dos sentidos")
        void idaYVuelta() {
            Track entity = mapper.toEntity(dto());
            assertThat(entity.getName()).isEqualTo("VIA 1");
            assertThat(entity.getEnabled()).isTrue();

            Track origen = new Track();
            origen.setId(3L);
            origen.setName("VIA 1");
            origen.setEnabled(false);

            TrackDTO resultado = mapper.toDTO(origen);
            assertThat(resultado.getId()).isEqualTo(3L);
            assertThat(resultado.getEnabled()).isFalse();
        }

        @Test
        @DisplayName("las propiedades de auditoria del DTO no se copian a la entidad")
        void auditoriaIgnorada() {
            TrackDTO dto = dto();
            dto.setCreateUser("intruso");
            dto.setCreateDate(LocalDateTime.of(2000, 1, 1, 0, 0));
            dto.setVersionNumber(99);

            Track entity = mapper.toEntity(dto);

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
    @DisplayName("Reconciliacion de perfiles")
    class Perfiles {

        @Test
        @DisplayName("un perfil que llega con id ACTUALIZA su fila en vez de duplicarla")
        void perfilEnviadoSeActualiza() {
            Track track = viaConTresPerfiles();
            Profile existente = track.getProfiles().getFirst();

            TrackDTO dto = dto();
            dto.setProfiles(new ArrayList<>(List.of(
                    profileDto(1L, "P-001-BIS", "11.000"),
                    profileDto(2L, "P-002", "20.000"),
                    profileDto(3L, "P-003", "30.000"))));

            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getProfiles()).hasSize(3);
            assertThat(track.getProfiles().getFirst()).isSameAs(existente);
            assertThat(existente.getProfileId()).isEqualTo("P-001-BIS");
            assertThat(existente.getKp()).isEqualByComparingTo("11.000");
        }

        @Test
        @DisplayName("modificar varias veces seguidas no acumula perfiles")
        void modificacionesRepetidasNoAcumulan() {
            // El caso que hacia inservible la tabla: 3 -> 6 -> 12 -> 24 en tres guardados.
            Track track = viaConTresPerfiles();

            for (int i = 0; i < 3; i++) {
                TrackDTO dto = dto();
                dto.setProfiles(new ArrayList<>(List.of(
                        profileDto(1L, "P-001", "1" + i + ".000"),
                        profileDto(2L, "P-002", "20.000"),
                        profileDto(3L, "P-003", "30.000"))));
                mapper.updateEntityFromDTO(dto, track);
            }

            assertThat(track.getProfiles()).hasSize(3);
        }

        @Test
        @DisplayName("una misma peticion conserva, edita, añade y borra a la vez")
        void reconciliacionCompleta() {
            Track track = viaConTresPerfiles();

            TrackDTO dto = dto();
            dto.setProfiles(new ArrayList<>(List.of(
                    profileDto(1L, "P-001-BIS", "11.000"),   // editado
                    profileDto(2L, "P-002", "20.000"),       // sin tocar
                    profileDto(null, "P-004", "40.000"))));  // nuevo; el 3 no viene, se retira

            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getProfiles())
                    .extracting(p -> p.getId())
                    .containsExactly(1L, 2L, null);
            assertThat(track.getProfiles())
                    .extracting(Profile::getProfileId)
                    .containsExactly("P-001-BIS", "P-002", "P-004");
            assertThat(track.getProfiles())
                    .allSatisfy(p -> assertThat(p.getTrack()).isSameAs(track));
        }

        @Test
        @DisplayName("el subarbol del perfil tampoco se duplica al modificar la via")
        void subarbolDelPerfil() {
            // Una copia del perfil se llevaria por delante sus mensulas: es el efecto en cascada
            // que hace que este fallo no se quede en una tabla.
            Track track = new Track();
            track.setName("VIA 1");
            Profile perfil = profile(1L, "P-001", "10.000");
            Cantilever mensula =
                    new Cantilever();
            mensula.setId(50L);
            mensula.setCwHeight(new BigDecimal("5.500"));
            perfil.setCantilevers(new ArrayList<>(List.of(mensula)));
            track.setProfiles(new ArrayList<>(List.of(perfil)));

            ProfileDTO perfilDto = profileDto(1L, "P-001", "10.000");
           CantileverDTO mensulaDto =
                    new CantileverDTO();
            mensulaDto.setId(50L);
            mensulaDto.setCwHeight(new BigDecimal("9.999"));
            perfilDto.setCantilevers(new ArrayList<>(List.of(mensulaDto)));

            TrackDTO dto = dto();
            dto.setProfiles(new ArrayList<>(List.of(perfilDto)));

            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getProfiles()).hasSize(1);
            assertThat(perfil.getCantilevers()).hasSize(1);
            assertThat(mensula.getCwHeight())
                    .as("la mensula se actualiza en el sitio, dos niveles por debajo")
                    .isEqualByComparingTo("9.999");
        }

        @Test
        @DisplayName("una lista de perfiles vacia retira todos los existentes")
        void listaVacia() {
            Track track = viaConTresPerfiles();

            TrackDTO dto = dto();
            dto.setProfiles(new ArrayList<>());

            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getProfiles()).isEmpty();
        }

        @Test
        @DisplayName("un id que no pertenece a esta via se ignora")
        void idAjenoSeIgnora() {
            Track track = new Track();
            track.setName("VIA 1");
            track.setProfiles(new ArrayList<>());

            TrackDTO dto = dto();
            dto.setProfiles(new ArrayList<>(List.of(profileDto(999L, "P-AJENO", "1.000"))));

            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getProfiles()).isEmpty();
        }

        @Test
        @DisplayName("un alta con perfiles nuevos los crea todos")
        void altaConPerfiles() {
            TrackDTO dto = dto();
            dto.setProfiles(new ArrayList<>(List.of(
                    profileDto(null, "P-001", "10.000"),
                    profileDto(null, "P-002", "20.000"))));

            Track track = mapper.toEntity(dto);

            assertThat(track.getProfiles())
                    .extracting(Profile::getProfileId)
                    .containsExactly("P-001", "P-002");
            assertThat(track.getProfiles())
                    .allSatisfy(p -> assertThat(p.getTrack()).isSameAs(track));
        }
    }

    @Nested
    @DisplayName("Estaciones")
    class Estaciones {

        @BeforeEach
        void referencias() {
            when(referenceMapper.resolve(anyLong(), eq(Station.class)))
                    .thenAnswer(invocation -> station(invocation.getArgument(0)));
        }

        @Test
        @DisplayName("una via larga se liga a las TRES estaciones que atraviesa")
        void variasEstaciones() {
            // 'TRACK 1' de EP4 pasa por ZIC, por BIN y por HAD. Con la clave ajena unica de
            // antes solo cabia una, y las otras dos se perdian al guardar.
            TrackDTO dto = dto();
            dto.setStationIds(List.of(7L, 8L, 9L));

            Track track = mapper.toEntity(dto);

            assertThat(track.getStations())
                    .extracting(each -> each.getId())
                    .containsExactlyInAnyOrder(7L, 8L, 9L);
        }

        @Test
        @DisplayName("la vuelta a DTO devuelve los ids de todas, ordenados")
        void vueltaADto() {
            Track track = new Track();
            track.setStations(new LinkedHashSet<>(List.of(station(9L), station(7L))));

            assertThat(mapper.toDTO(track).getStationIds()).containsExactly(7L, 9L);
        }

        @Test
        @DisplayName("una lista vacia desliga: la via pasa a colgar del paquete")
        void listaVacia() {
            Track track = new Track();
            track.setStations(new LinkedHashSet<>(List.of(station(7L))));

            TrackDTO dto = dto();
            dto.setStationIds(List.of());
            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getStations()).isEmpty();
        }

        @Test
        @DisplayName("un TrackDTO recien construido NO trae la lista, y por eso no borra nada")
        void porDefectoNoTraeLaLista() {
            // Es el default del DTO, no una manía: una via anidada dentro de una estacion se
            // construye asi, sin tocar stationIds. Cuando el campo se inicializaba a lista
            // vacia, esa via llegaba al mapper pidiendo "quitame todas las estaciones" y se
            // llevaba por delante las otras dos por las que pasa. Un PUT sobre ZIC dejaba a
            // 'TRACK 1' fuera de BIN y de HAD sin que nadie lo pidiera.
            Track track = new Track();
            track.setStations(new LinkedHashSet<>(List.of(station(7L), station(8L))));

            mapper.updateEntityFromDTO(new TrackDTO(), track);

            assertThat(track.getStations()).hasSize(2);
        }

        @Test
        @DisplayName("no mandar el campo no toca nada, que no es lo mismo que mandarlo vacio")
        void campoAusente() {
            // La diferencia importa: un cliente que solo renombra la via manda el nombre y no
            // la lista, y no tiene por que perder las estaciones por el camino.
            Track track = new Track();
            track.setStations(new LinkedHashSet<>(List.of(station(7L))));

            TrackDTO dto = dto();
            dto.setStationIds(null);
            mapper.updateEntityFromDTO(dto, track);

            assertThat(track.getStations()).extracting(each -> each.getId()).containsExactly(7L);
        }

        private static Station station(Long id) {
            Station station = new Station();
            station.setId(id);
            return station;
        }
    }
}
