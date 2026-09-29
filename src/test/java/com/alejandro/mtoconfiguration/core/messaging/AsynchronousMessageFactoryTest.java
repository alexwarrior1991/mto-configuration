package com.alejandro.mtoconfiguration.core.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La factoria es el unico sitio donde el sobre recibe su contexto: si dejara de preguntar por el
 * actor o por la correlacion, todos los eventos saldrian sin ellos y ningun otro test lo veria,
 * porque el publicador de datos maestros y el de trabajos reciben el sobre ya hecho.
 */
class AsynchronousMessageFactoryTest {

    private static final MessageActor ACTOR = MessageActor.of("6f1b1c8e-0000-4000-8000-000000000001", "ana.perez");
    private static final String CORRELATION_ID = "8c3b8c1a-1111-4222-8333-444444444444";

    private final AsynchronousMessageHashService hashService = new AsynchronousMessageHashService(new ObjectMapper());
    private final MessageContextResolver contextResolver = mock(MessageContextResolver.class);

    private AsynchronousMessageFactory factory;

    @BeforeEach
    void setUp() {
        when(contextResolver.currentActor()).thenReturn(ACTOR);
        when(contextResolver.currentCorrelationId()).thenReturn(CORRELATION_ID);

        factory = new AsynchronousMessageFactory(hashService, contextResolver);
        ReflectionTestUtils.setField(factory, "applicationName", "mto-configuration");
    }

    @Test
    @DisplayName("el sobre sale con el origen, el actor y la correlacion del contexto en el que se crea")
    void elSobreLlevaElContexto() {
        AsynchronousMessage<Map<String, Object>> message = factory.create(
                "station-10", "MASTER_DATA_STATION_UPDATED", Map.of("id", 10));

        assertThat(message.origin()).isEqualTo("mto-configuration");
        assertThat(message.actor()).isEqualTo(ACTOR);
        assertThat(message.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(message.operationId()).isNotNull();
        assertThat(message.creationDate()).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(1));
        assertThat(message.messageHash()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("un operationId dado se respeta: es lo que hace idempotente a un evento repetido en destino")
    void elOperationIdDadoSeRespeta() {
        UUID operationId = UUID.fromString("00000000-0000-4000-8000-000000000042");

        AsynchronousMessage<Map<String, Object>> message = factory.create(
                operationId, "job-1", "CONFIGURATION_JOB_FINISHED", Map.of());

        assertThat(message.operationId()).isEqualTo(operationId);
    }

    @Test
    @DisplayName("la huella no incluye el actor ni la correlacion: siguen siendo las siete claves del contrato original")
    void laHuellaIgnoraElContexto() {
        AsynchronousMessage<Map<String, Object>> withContext = factory.create(
                "station-10", "MASTER_DATA_STATION_UPDATED", Map.of("id", 10));

        AsynchronousMessage<Map<String, Object>> withoutContext = new AsynchronousMessage<>(
                withContext.operationId(),
                withContext.referenceId(),
                withContext.origin(),
                withContext.creationDate(),
                withContext.eventType(),
                withContext.data(),
                "PENDING",
                null,
                null
        );

        // Un consumidor que ya calculaba la huella sobre las siete claves sigue obteniendo la misma:
        // anadir claves al sobre no le cambia nada.
        assertThat(hashService.calculate(withoutContext)).isEqualTo(withContext.messageHash());
    }
}
