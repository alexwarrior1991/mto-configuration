package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mapeo real de mensulas, contra la implementacion que genera MapStruct.
 *
 * <p>Se prueba {@code CantileverMapperImpl} y no una clase escrita a mano: lo que puede romperse
 * no es el codigo generado sino las <b>instrucciones</b> que se le dan —los {@code @Mapping}, los
 * {@code ignore}, los {@code @AfterMapping}— y eso solo se ve ejecutando el generado. Un
 * {@code ignore} de mas deja un campo a null en silencio.</p>
 *
 * <p>Los dos comportamientos criticos: que las propiedades de auditoria <b>no</b> viajen del DTO a
 * la entidad (las pone la capa de auditoria, y dejar que las mande el cliente permite falsear quien
 * creo un registro) y que las LOV se resuelvan contra el catalogo por codigo en lugar de
 * persistirse tal como llegan.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CantileverMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private CantileverMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).cantilever;
    }

    private static CantileverType cantileverType(Long id, String code) {
        CantileverType entity = new CantileverType();
        entity.setId(id);
        entity.setCode(code);
        entity.setDescription("Tipo " + code);
        entity.setEnabled(true);
        return entity;
    }

    private static CantileverDTO dto() {
        CantileverDTO dto = new CantileverDTO();
        dto.setCwHeight(new BigDecimal("1.100"));
        dto.setStagger(new BigDecimal("200"));
        dto.setCatenaryHeight(new BigDecimal("5.500"));
        return dto;
    }

    @Test
    @DisplayName("los campos propios viajan del DTO a la entidad")
    void camposPropios() {
        Cantilever entity = mapper.toEntity(dto());

        assertThat(entity.getCwHeight()).isEqualByComparingTo("1.100");
        assertThat(entity.getStagger()).isEqualByComparingTo("200");
        assertThat(entity.getCatenaryHeight()).isEqualByComparingTo("5.500");
    }

    @Test
    @DisplayName("las propiedades de auditoria del DTO NO se copian a la entidad")
    void auditoriaIgnorada() {
        CantileverDTO dto = dto();
        dto.setCreateUser("intruso");
        dto.setVersionUser("intruso");
        dto.setCreateDate(LocalDateTime.of(2000, 1, 1, 0, 0));
        dto.setVersionDate(LocalDateTime.of(2000, 1, 1, 0, 0));
        dto.setVersionNumber(99);

        Cantilever entity = mapper.toEntity(dto);

        assertThat(entity.getCreateUser()).isNull();
        assertThat(entity.getVersionUser()).isNull();
        assertThat(entity.getCreateDate()).isNull();
        assertThat(entity.getVersionDate()).isNull();
        assertThat(entity.getVersionNumber()).as("se conserva el valor inicial de la entidad").isEqualTo(1);
    }

    @Test
    @DisplayName("el tipo de mensula se resuelve contra el catalogo por codigo, no se copia")
    void lovResueltaPorCodigo() {
        CantileverType resuelto = cantileverType(7L, "SIM");
        when(masterDataService.getCantileverTypeByCode("SIM")).thenReturn(resuelto);

        CantileverDTO dto = dto();
        CantileverTypeDTO tipo = new CantileverTypeDTO();
        tipo.setCode("SIM");
        tipo.setDescription("descripcion que manda el cliente y da igual");
        dto.setCantileverType(tipo);

        Cantilever entity = mapper.toEntity(dto);

        assertThat(entity.getCantileverType()).isSameAs(resuelto);
    }

    @Test
    @DisplayName("un tipo de mensula sin codigo no se consulta al catalogo")
    void lovSinCodigo() {
        CantileverDTO dto = dto();
        dto.setCantileverType(new CantileverTypeDTO());

        Cantilever entity = mapper.toEntity(dto);

        assertThat(entity.getCantileverType()).isNull();
        verifyNoInteractions(masterDataService);
    }

    @Test
    @DisplayName("el perfil padre se resuelve como referencia a partir de su id")
    void perfilPorReferencia() {
        Profile profile = new Profile();
        profile.setId(3L);
        when(referenceMapper.resolve(eq(3L), any())).thenReturn(profile);

        CantileverDTO dto = dto();
        dto.setProfileId(3L);

        assertThat(mapper.toEntity(dto).getProfile()).isSameAs(profile);
    }

    @Test
    @DisplayName("el brazo de atirantado queda apuntando a su mensula")
    void vinculoBidireccionalConElBrazo() {
        // Sin este vinculo el brazo se persiste con la clave ajena a null y queda huerfano.
        CantileverDTO dto = dto();
        SteadyArmDTO steadyArm = new SteadyArmDTO();
        steadyArm.setLength(200L);
        SteadyArmTypeDTO tipo = new SteadyArmTypeDTO();
        tipo.setCode("CRT");
        steadyArm.setSteadyArmType(tipo);
        dto.setSteadyArm(steadyArm);

        Cantilever entity = mapper.toEntity(dto);

        assertThat(entity.getSteadyArm()).isNotNull();
        assertThat(entity.getSteadyArm().getCantilever()).isSameAs(entity);
    }

    @Test
    @DisplayName("de entidad a DTO el tipo se enriquece desde el catalogo por id")
    void lovEnriquecidaPorId() {
        CantileverTypeDTO oficial = new CantileverTypeDTO();
        oficial.setId(7L);
        oficial.setCode("SIM");
        oficial.setDescription("Simple");
        when(masterDataService.getCantileverTypeByIdAndMapToDTO(7L)).thenReturn(oficial);

        Cantilever entity = new Cantilever();
        entity.setId(1L);
        entity.setCwHeight(new BigDecimal("1.100"));
        entity.setCantileverType(cantileverType(7L, "SIM"));

        CantileverDTO dto = mapper.toDTO(entity);

        assertThat(dto.getCantileverType()).isSameAs(oficial);
        assertThat(dto.getCwHeight()).isEqualByComparingTo("1.100");
    }

    @Test
    @DisplayName("de entidad a DTO el perfil padre viaja como id, no como objeto")
    void perfilComoId() {
        Profile profile = new Profile();
        profile.setId(3L);

        Cantilever entity = new Cantilever();
        entity.setProfile(profile);

        assertThat(mapper.toDTO(entity).getProfileId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("una entidad sin tipo ni perfil se mapea sin consultar el catalogo")
    void entidadMinima() {
        Cantilever entity = new Cantilever();
        entity.setCwHeight(new BigDecimal("1.100"));

        CantileverDTO dto = mapper.toDTO(entity);

        assertThat(dto.getCantileverType()).isNull();
        assertThat(dto.getProfileId()).isNull();
        verifyNoInteractions(masterDataService);
    }

    @Test
    @DisplayName("un nulo se mapea a nulo en los dos sentidos")
    void nulos() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDTO(null)).isNull();
    }

    @Test
    @DisplayName("la modificacion sobre una entidad existente tampoco pisa la auditoria")
    void actualizacionRespetaAuditoria() {
        Cantilever entity = new Cantilever();
        entity.setCreateUser("ana");
        entity.setCreateDate(LocalDateTime.of(2026, 1, 1, 10, 0));
        entity.setVersionNumber(5);

        CantileverDTO dto = dto();
        dto.setCreateUser("intruso");
        dto.setVersionNumber(99);

        mapper.updateEntityFromDTO(dto, entity);

        assertThat(entity.getCreateUser()).isEqualTo("ana");
        assertThat(entity.getCreateDate()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(entity.getVersionNumber()).isEqualTo(5);
        assertThat(entity.getCwHeight()).as("los campos de negocio si se actualizan")
                .isEqualByComparingTo("1.100");
    }

    @Test
    @DisplayName("de entidad a DTO si viajan las propiedades de auditoria")
    void auditoriaHaciaElDto() {
        // La restriccion es de entrada, no de salida: el cliente debe poder ver quien y cuando.
        Cantilever entity = new Cantilever();
        entity.setCreateUser("ana");
        entity.setVersionNumber(3);

        CantileverDTO dto = mapper.toDTO(entity);

        assertThat(dto.getCreateUser()).isEqualTo("ana");
        assertThat(dto.getVersionNumber()).isEqualTo(3);
    }
}
