package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PoleTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mapeo real de perfiles, contra la implementacion que genera MapStruct.
 *
 * <p>Es el mapper mas cargado del proyecto: ocho listas de valores que se resuelven contra el
 * catalogo, una coleccion de mensulas que hay que sincronizar y una relacion uno a uno con el
 * seccionador. Nada de eso lo genera MapStruct solo, va en los {@code @AfterMapping} escritos a
 * mano, y por tanto nada de eso lo protege el compilador.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private ProfileMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).profile;
    }

    private static ProfileDTO dto() {
        ProfileDTO dto = new ProfileDTO();
        dto.setProfileId("P-001");
        dto.setKp("10.500");
        return dto;
    }

    private static CantileverDTO cantilever(Long id) {
        CantileverDTO dto = new CantileverDTO();
        dto.setId(id);
        dto.setCwHeight(new BigDecimal("1.100"));
        dto.setStagger(new BigDecimal("200"));
        return dto;
    }

    @Nested
    @DisplayName("Campos propios")
    class CamposPropios {

        @Test
        @DisplayName("el punto kilometrico viaja como texto en el DTO y como numero en la entidad")
        void kilometrico() {
            Profile entity = mapper.toEntity(dto());

            assertThat(entity.getProfileId()).isEqualTo("P-001");
            assertThat(entity.getKp()).isEqualByComparingTo("10.500");
        }

        @Test
        @DisplayName("de vuelta, el punto kilometrico se rinde como texto")
        void kilometricoDeVuelta() {
            Profile entity = new Profile();
            entity.setProfileId("P-001");
            entity.setKp(new BigDecimal("10.500"));

            assertThat(mapper.toDTO(entity).getKp()).isEqualTo("10.500");
        }

        @Test
        @DisplayName("un nulo se mapea a nulo en los dos sentidos")
        void nulos() {
            assertThat(mapper.toEntity(null)).isNull();
            assertThat(mapper.toDTO(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Auditoria")
    class Auditoria {

        @Test
        @DisplayName("las propiedades de auditoria del DTO NO se copian a la entidad")
        void auditoriaIgnorada() {
            ProfileDTO dto = dto();
            dto.setCreateUser("intruso");
            dto.setVersionUser("intruso");
            dto.setCreateDate(LocalDateTime.of(2000, 1, 1, 0, 0));
            dto.setVersionNumber(99);

            Profile entity = mapper.toEntity(dto);

            assertThat(entity.getCreateUser()).isNull();
            assertThat(entity.getVersionUser()).isNull();
            assertThat(entity.getCreateDate()).isNull();
            assertThat(entity.getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("la modificacion sobre una entidad existente tampoco pisa la auditoria")
        void actualizacion() {
            Profile entity = new Profile();
            entity.setCreateUser("ana");
            entity.setVersionNumber(5);

            ProfileDTO dto = dto();
            dto.setCreateUser("intruso");
            dto.setVersionNumber(99);

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getCreateUser()).isEqualTo("ana");
            assertThat(entity.getVersionNumber()).isEqualTo(5);
            assertThat(entity.getProfileId()).isEqualTo("P-001");
        }
    }

    @Nested
    @DisplayName("Listas de valores")
    class ListasDeValores {

        @Test
        @DisplayName("cada LOV se resuelve contra el catalogo por su codigo")
        void resolucionPorCodigo() {
            PoleType poleType = new PoleType();
            poleType.setId(1L);
            poleType.setCode("PT1");
            ProfileStatus status = new ProfileStatus();
            status.setId(2L);
            status.setCode("OK");

            when(masterDataService.getPoleTypeByCode("PT1")).thenReturn(poleType);
            when(masterDataService.getProfileStatusByCode("OK")).thenReturn(status);

            ProfileDTO dto = dto();
            PoleTypeDTO poleTypeDto = new PoleTypeDTO();
            poleTypeDto.setCode("PT1");
            dto.setPoleType(poleTypeDto);
            ProfileStatusDTO statusDto = new ProfileStatusDTO();
            statusDto.setCode("OK");
            dto.setProfileStatus(statusDto);

            Profile entity = mapper.toEntity(dto);

            assertThat(entity.getPoleType()).isSameAs(poleType);
            assertThat(entity.getProfileStatus()).isSameAs(status);
        }

        @Test
        @DisplayName("una LOV ausente en el DTO no se consulta ni se asigna")
        void lovAusente() {
            Profile entity = mapper.toEntity(dto());

            assertThat(entity.getPoleType()).isNull();
            assertThat(entity.getProfileStatus()).isNull();
            assertThat(entity.getAnchorage()).isNull();
            verifyNoInteractions(masterDataService);
        }

        @Test
        @DisplayName("de entidad a DTO las LOV se enriquecen desde el catalogo por id")
        void enriquecimientoPorId() {
            PoleTypeDTO oficial = new PoleTypeDTO();
            oficial.setId(1L);
            oficial.setCode("PT1");
            oficial.setDescription("Poste tipo 1");
            when(masterDataService.getPoleTypeByIdAndMapToDTO(1L)).thenReturn(oficial);

            PoleType poleType = new PoleType();
            poleType.setId(1L);
            poleType.setCode("PT1");

            Profile entity = new Profile();
            entity.setProfileId("P-001");
            entity.setPoleType(poleType);

            assertThat(mapper.toDTO(entity).getPoleType()).isSameAs(oficial);
        }
    }

    @Nested
    @DisplayName("Relaciones")
    class Relaciones {

        @Test
        @DisplayName("la via se resuelve como referencia a partir de su id")
        void viaPorReferencia() {
            Track track = new Track();
            track.setId(3L);
            when(referenceMapper.resolve(eq(3L), any())).thenReturn(track);

            ProfileDTO dto = dto();
            dto.setTrackId(3L);

            assertThat(mapper.toEntity(dto).getTrack()).isSameAs(track);
        }

        @Test
        @DisplayName("de entidad a DTO la via viaja como id, no como objeto")
        void viaComoId() {
            Track track = new Track();
            track.setId(3L);

            Profile entity = new Profile();
            entity.setTrack(track);

            assertThat(mapper.toDTO(entity).getTrackId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("cada mensula queda apuntando a su perfil")
        void mensulasVinculadas() {
            // Sin este vinculo las mensulas se persisten con la clave ajena a null.
            ProfileDTO dto = dto();
            dto.setCantilevers(new ArrayList<>(List.of(cantilever(null), cantilever(null))));

            Profile entity = mapper.toEntity(dto);

            assertThat(entity.getCantilevers()).hasSize(2);
            assertThat(entity.getCantilevers())
                    .allSatisfy(c -> assertThat(c.getProfile()).isSameAs(entity));
        }

        @Test
        @DisplayName("al modificar, una mensula existente que no viene en el DTO se retira")
        void mensulaHuerfanaSeRetira() {
            // linkCollection se invoca con deleteEntitiesNotInDtoList = true: lo que no viene en
            // el DTO se considera borrado por el cliente.
            Profile entity = new Profile();
            Cantilever conservada = new Cantilever();
            conservada.setId(1L);
            Cantilever retirada = new Cantilever();
            retirada.setId(2L);
            entity.setCantilevers(new ArrayList<>(List.of(conservada, retirada)));

            ProfileDTO dto = dto();
            dto.setCantilevers(new ArrayList<>(List.of(cantilever(1L))));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getCantilevers())
                    .extracting(c -> c.getId())
                    .as("la mensula 2, ausente del DTO, ya no esta")
                    .doesNotContain(2L);
        }

        @Test
        @DisplayName("al modificar, la mensula que llega con id ACTUALIZA su fila en vez de duplicarla")
        void mensulaEnviadaSeActualiza() {
            // Este es el caso que antes duplicaba: el cliente devuelve la mensula que ve, con su
            // id, y el mapeo creaba una copia sin id junto a la original, dejando la original sin
            // los cambios. mergeCollection vuelca el DTO sobre la instancia que ya esta.
            Profile entity = new Profile();
            Cantilever existente = new Cantilever();
            existente.setId(1L);
            existente.setCwHeight(new BigDecimal("5.500"));
            entity.setCantilevers(new ArrayList<>(List.of(existente)));

            ProfileDTO dto = dto();
            CantileverDTO editada = cantilever(1L);
            editada.setCwHeight(new BigDecimal("9.999"));
            dto.setCantilevers(new ArrayList<>(List.of(editada)));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getCantilevers()).hasSize(1);
            assertThat(entity.getCantilevers().getFirst())
                    .as("la MISMA instancia, no una copia")
                    .isSameAs(existente);
            assertThat(existente.getCwHeight()).isEqualByComparingTo("9.999");
        }

        @Test
        @DisplayName("modificar varias veces seguidas no acumula filas")
        void modificacionesRepetidasNoAcumulan() {
            Profile entity = new Profile();
            Cantilever existente = new Cantilever();
            existente.setId(1L);
            entity.setCantilevers(new ArrayList<>(List.of(existente)));

            for (String altura : List.of("6.000", "6.500", "7.000")) {
                ProfileDTO dto = dto();
                CantileverDTO editada = cantilever(1L);
                editada.setCwHeight(new BigDecimal(altura));
                dto.setCantilevers(new ArrayList<>(List.of(editada)));

                mapper.updateEntityFromDTO(dto, entity);
            }

            assertThat(entity.getCantilevers()).hasSize(1);
            assertThat(existente.getCwHeight()).isEqualByComparingTo("7.000");
        }

        @Test
        @DisplayName("una misma peticion conserva, edita, añade y borra a la vez")
        void reconciliacionCompleta() {
            Profile entity = new Profile();
            Cantilever editada = new Cantilever();
            editada.setId(1L);
            editada.setCwHeight(new BigDecimal("5.500"));
            Cantilever intacta = new Cantilever();
            intacta.setId(2L);
            intacta.setCwHeight(new BigDecimal("6.000"));
            Cantilever borrada = new Cantilever();
            borrada.setId(3L);
            entity.setCantilevers(new ArrayList<>(List.of(editada, intacta, borrada)));

            ProfileDTO dto = dto();
            CantileverDTO conCambio = cantilever(1L);
            conCambio.setCwHeight(new BigDecimal("9.999"));
            CantileverDTO sinCambio = cantilever(2L);
            sinCambio.setCwHeight(new BigDecimal("6.000"));
            CantileverDTO nueva = cantilever(null);
            nueva.setCwHeight(new BigDecimal("7.777"));
            dto.setCantilevers(new ArrayList<>(List.of(conCambio, sinCambio, nueva)));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getCantilevers())
                    .extracting(c -> c.getId())
                    .as("la 3 se retira, la nueva entra sin id")
                    .containsExactly(1L, 2L, null);
            assertThat(editada.getCwHeight()).isEqualByComparingTo("9.999");
            assertThat(intacta.getCwHeight()).isEqualByComparingTo("6.000");
            assertThat(entity.getCantilevers())
                    .allSatisfy(c -> assertThat(c.getProfile()).isSameAs(entity));
        }

        @Test
        @DisplayName("un id que no pertenece a este perfil se ignora, no inserta una fila")
        void idAjenoSeIgnora() {
            // Aceptarlo crearia una mensula a partir de datos de otro perfil.
            Profile entity = new Profile();
            entity.setCantilevers(new ArrayList<>());

            ProfileDTO dto = dto();
            dto.setCantilevers(new ArrayList<>(List.of(cantilever(999L))));

            mapper.updateEntityFromDTO(dto, entity);

            assertThat(entity.getCantilevers()).isEmpty();
        }

        @Test
        @DisplayName("el seccionador queda apuntando a su perfil")
        void seccionadorVinculado() {
            ProfileDTO dto = dto();
            DisconnectorDTO disconnector = new DisconnectorDTO();
            disconnector.setName("SECC-1");
            disconnector.setOnLoad(true);
            dto.setDisconnector(disconnector);

            Profile entity = mapper.toEntity(dto);

            assertThat(entity.getDisconnector()).isNotNull();
            assertThat(entity.getDisconnector().getProfile()).isSameAs(entity);
        }

        @Test
        @DisplayName("un perfil sin seccionador se mapea sin intentar vincularlo")
        void sinSeccionador() {
            Profile entity = mapper.toEntity(dto());

            assertThat(entity.getDisconnector()).isNull();
        }

        @Test
        @DisplayName("de entidad a DTO las mensulas viajan como lista de DTO")
        void mensulasHaciaElDto() {
            Cantilever cantilever = new Cantilever();
            cantilever.setId(1L);
            cantilever.setCwHeight(new BigDecimal("1.100"));

            Profile entity = new Profile();
            entity.setProfileId("P-001");
            entity.setCantilevers(new ArrayList<>(List.of(cantilever)));

            assertThat(mapper.toDTO(entity).getCantilevers())
                    .extracting(CantileverDTO::getId)
                    .containsExactly(1L);
        }

        @Test
        @DisplayName("de entidad a DTO el seccionador viaja anidado")
        void seccionadorHaciaElDto() {
            Disconnector disconnector = new Disconnector();
            disconnector.setId(1L);
            disconnector.setName("SECC-1");
            disconnector.setOnLoad(true);

            Profile entity = new Profile();
            entity.setProfileId("P-001");
            entity.setDisconnector(disconnector);

            assertThat(mapper.toDTO(entity).getDisconnector()).isNotNull();
            assertThat(mapper.toDTO(entity).getDisconnector().getName()).isEqualTo("SECC-1");
        }
    }

    @Nested
    @DisplayName("Listas")
    class Listas {

        @Test
        @DisplayName("las conversiones de lista respetan el orden y el tamaño")
        void listas() {
            Profile primero = new Profile();
            primero.setProfileId("P-001");
            Profile segundo = new Profile();
            segundo.setProfileId("P-002");

            assertThat(mapper.toListDTO(List.of(primero, segundo)))
                    .extracting(ProfileDTO::getProfileId)
                    .containsExactly("P-001", "P-002");
        }

        @Test
        @DisplayName("una lista nula se mapea a nulo")
        void listaNula() {
            assertThat(mapper.toListDTO(null)).isNull();
            assertThat(mapper.toListEntity(null)).isNull();
        }
    }
}
