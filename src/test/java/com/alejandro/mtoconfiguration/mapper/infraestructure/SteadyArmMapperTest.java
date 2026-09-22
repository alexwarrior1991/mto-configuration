package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * El brazo de atirantado: lo unico que tiene de propio es su mensula, y es justo lo que el detalle
 * perdia (ver {@code BaseMapper.updateDTOFromEntity}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SteadyArmMapperTest {

    @Mock
    private MasterDataService masterDataService;
    @Mock
    private ReferenceMapper referenceMapper;

    private SteadyArmMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MapperGraph(masterDataService, referenceMapper).steadyArm;
    }

    private static SteadyArm brazo() {
        Cantilever cantilever = new Cantilever();
        cantilever.setId(21L);
        SteadyArm entity = new SteadyArm();
        entity.setId(31L);
        entity.setLength(1200L);
        entity.setCantilever(cantilever);
        return entity;
    }

    @Test
    @DisplayName("de entidad a DTO la mensula viaja como id, no como objeto")
    void mensulaComoId() {
        SteadyArmDTO dto = mapper.toDTO(brazo());

        assertThat(dto.getId()).isEqualTo(31L);
        assertThat(dto.getLength()).isEqualTo(1200L);
        assertThat(dto.getCantileverId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("el detalle de un brazo lleva el id de su mensula, como las listas")
    void mensulaComoIdEnElDetalle() {
        SteadyArmDTO dto = new SteadyArmDTO();
        mapper.updateDTOFromEntity(brazo(), dto);

        assertThat(dto.getId()).isEqualTo(31L);
        assertThat(dto.getCantileverId()).isEqualTo(21L);
    }
}
