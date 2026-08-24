package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.CantileverMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.SteadyArmMasterDataPayloadMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El extractor tenia un camino por defecto que serializaba la entidad JPA con Jackson
 * cuando no habia mapper. Estos tests fijan que ese atajo ya no existe y que el ciclo
 * SteadyArm <-> Cantilever queda cortado.
 */
class MasterDataEventPayloadExtractorTest {

    private final MasterDataEventPayloadExtractor extractor = new MasterDataEventPayloadExtractor(
            List.of(new SteadyArmMasterDataPayloadMapper(), new CantileverMasterDataPayloadMapper())
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SteadyArm y Cantilever apuntandose mutuamente, como quedan tras un save real. */
    private SteadyArm steadyArmConCicloCompleto() {
        SteadyArmType type = new SteadyArmType();
        type.setId(7L);
        type.setCode("SA-01");
        type.setDescription("Tipo de mensula");

        SteadyArm steadyArm = new SteadyArm();
        steadyArm.setId(1L);
        steadyArm.setLength(1200L);
        steadyArm.setSteadyArmType(type);

        Cantilever cantilever = new Cantilever();
        cantilever.setId(99L);
        cantilever.setSteadyArm(steadyArm);

        steadyArm.setCantilever(cantilever);

        return steadyArm;
    }

    @Test
    void elPayloadDeSteadyArmNoArrastraElCicloConCantilever() {
        Map<String, Object> payload = extractor.extract(steadyArmConCicloCompleto());

        assertThat(payload)
                .containsEntry("id", 1L)
                .containsEntry("length", 1200L)
                .containsEntry("cantileverId", 99L);

        assertThat(payload.get("steadyArmType"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "SA-01");
    }

    @Test
    void elPayloadDeSteadyArmSeSerializaSinRecursionInfinita() {
        Map<String, Object> payload = extractor.extract(steadyArmConCicloCompleto());

        // Antes esto era un StackOverflowError DENTRO de la transaccion de negocio,
        // de modo que cualquier alta o modificacion de SteadyArm terminaba en error.
        assertThatCode(() -> objectMapper.writeValueAsString(payload)).doesNotThrowAnyException();
    }

    @Test
    void elPayloadDeCantileverNoArrastraElCicloConSteadyArm() {
        SteadyArm steadyArm = steadyArmConCicloCompleto();

        Map<String, Object> payload = extractor.extract(steadyArm.getCantilever());

        assertThat(payload).containsEntry("id", 99L);
        assertThat(payload.get("steadyArm"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("id", 1L)
                .doesNotContainKey("cantilever");

        assertThatCode(() -> objectMapper.writeValueAsString(payload)).doesNotThrowAnyException();
    }

    @Test
    void unaEntidadSinMapperFallaConUnMensajeQueExplicaQueHacer() {
        assertThatThrownBy(() -> extractor.extract(new Object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@PublishMasterDataEvent")
                .hasMessageContaining("MasterDataEntityPayloadMapper");
    }

    @Test
    void unaEntidadNulaSigueSiendoUnErrorDeProgramacion() {
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
