package com.alejandro.mtoconfiguration.mapper.infraestructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
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

import static org.assertj.core.api.Assertions.assertThat;

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
}
