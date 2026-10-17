package com.alejandro.mtoconfiguration.core.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataChangedEvent;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEventTypeBuilder;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataOperation;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.TrackAccessPointMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.service.infraestructure.jobs.JobFinishedEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los ejemplos de {@code docs/messaging/examples} son el contrato tal como lo ven los consumidores,
 * y de ahi los copian como fixtures. Un ejemplo escrito a mano se desalinea del codigo sin que nadie
 * lo note; este test lo evita construyendo cada mensaje con la factoria real y comparandolo con el
 * fichero, salvo las dos claves que cambian en cada ejecucion (la fecha y, con ella, la huella),
 * que se comprueban aparte: la huella del fichero es la que un consumidor calcularia sobre sus
 * siete claves originales.
 */
class MessagingContractExamplesTest {

    private static final Path EXAMPLES = Path.of("docs", "messaging", "examples");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AsynchronousMessageHashService hashService = new AsynchronousMessageHashService(objectMapper);

    @Test
    @DisplayName("un evento de datos maestros sale como el ejemplo, con el actor y la correlacion de la peticion")
    void elEventoDeDatosMaestrosSaleComoElEjemplo() throws IOException {
        JsonNode example = example("master-data-steady-arm-updated.json");

        Map<String, Object> steadyArmType = new LinkedHashMap<>();
        steadyArmType.put("id", 7L);
        steadyArmType.put("code", "ATI1");
        steadyArmType.put("description", "Atirantado tipo 1");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", 321L);
        values.put("length", 1200L);
        values.put("steadyArmType", steadyArmType);
        values.put("cantileverId", 45L);

        MasterDataChangedEvent data = new MasterDataChangedEvent(
                "steady-arm", "321", MasterDataOperation.UPDATED, values);

        AsynchronousMessage<MasterDataChangedEvent> message = factory(
                MessageActor.of("6f1b1c8e-0000-4000-8000-000000000001", "config.editor"),
                "8c3b8c1a-1111-4222-8333-444444444444")
                .create(UUID.fromString("3f6d1f0e-7f2a-4b53-9d0e-5e0f4a1c2b3d"),
                        "steady-arm-321", "MASTER_DATA_STEADY_ARM_UPDATED", data);

        assertSameAsExample(message, example);
    }

    /**
     * El punto de acceso a la via (V28), que materializa {@code mto-resources}. Los {@code values}
     * salen del mapper de payload real, no escritos a mano: el ejemplo fija las claves y su forma tal
     * como las produce el codigo, con la via y el id de su paquete, y la estacion.
     */
    @Test
    @DisplayName("un punto de acceso a la via sale como el ejemplo, con los values de su mapper real")
    void elPuntoDeAccesoSaleComoElEjemplo() throws IOException {
        JsonNode example = example("master-data-track-access-point-updated.json");

        ExecutionPackage executionPackage = new ExecutionPackage();
        executionPackage.setId(14L);

        Track track = new Track();
        track.setId(3L);
        track.setName("TRACK 1");
        track.setExecutionPackage(executionPackage);

        Station station = new Station();
        station.setId(12L);
        station.setName("ASHKELON");

        TrackAccessPoint accessPoint = new TrackAccessPoint();
        accessPoint.setId(77L);
        accessPoint.setName("Acceso km 110+176");
        accessPoint.setEnabled(true);
        accessPoint.setKp(new BigDecimal("110176.000"));
        accessPoint.setKind(TrackAccessPointKind.PREPARED_ACCESS);
        accessPoint.setSide(TrackAccessPointSide.LEFT);
        accessPoint.setRoadRailAccess(true);
        accessPoint.setMaxAxleLoadT(new BigDecimal("12.500"));
        accessPoint.setMaxGrossWeightT(new BigDecimal("40.000"));
        accessPoint.setLatitude(new BigDecimal("31.669342"));
        accessPoint.setLongitude(new BigDecimal("34.574119"));
        accessPoint.setStation(station);
        accessPoint.setTrack(track);

        MasterDataChangedEvent data = new MasterDataChangedEvent("track-access-point", "77",
                MasterDataOperation.UPDATED, new TrackAccessPointMasterDataPayloadMapper().toPayload(accessPoint));

        AsynchronousMessage<MasterDataChangedEvent> message = factory(
                MessageActor.of("6f1b1c8e-0000-4000-8000-000000000001", "config.editor"),
                "5d2e7f10-2222-4333-8444-555555555555")
                .create(UUID.fromString("7a1c2b3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),
                        "track-access-point-77",
                        MasterDataEventTypeBuilder.build("track-access-point", MasterDataOperation.UPDATED), data);

        assertSameAsExample(message, example);
        assertThat(example.at("/data/values").propertyNames())
                .as("las claves de values que fija el contrato con mto-resources, en su orden")
                .containsExactly("id", "name", "enabled", "kp", "kind", "side", "roadRailAccess",
                        "maxAxleLoadT", "maxGrossWeightT", "latitude", "longitude", "station", "track");
        assertThat(example.at("/data/values/track").propertyNames())
                .containsExactly("id", "name", "executionPackageId");
    }

    @Test
    @DisplayName("el final de un trabajo sale como el ejemplo, con quien lo lanzo y el jobId como correlacion")
    void elFinalDeUnTrabajoSaleComoElEjemplo() throws IOException {
        JsonNode example = example("job-finished.json");

        UUID jobId = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

        AsyncJob job = new AsyncJob();
        job.setId(jobId);
        job.setType(JobType.LOV_IMPORT);
        job.setStatus(JobStatus.COMPLETED_WITH_ERRORS);
        job.setCreatedBy("config.responsable");
        job.setCreatedAt(Instant.parse("2026-09-29T07:00:00Z"));
        job.setStartedAt(Instant.parse("2026-09-29T07:00:01Z"));
        job.setFinishedAt(Instant.parse("2026-09-29T07:00:09Z"));
        job.setTotalItems(120);
        job.setProcessedItems(120);
        job.setSuccessfulItems(118);
        job.setFailedItems(2);
        job.setFileName("lov-import-" + jobId + ".json");

        DomainEvent data = new DomainEvent("job", jobId.toString(), "finished",
                JobFinishedEventPublisher.values(job));

        AsynchronousMessage<DomainEvent> message = factory(
                MessageActor.of("6f1b1c8e-0000-4000-8000-000000000002", "config.responsable"),
                jobId.toString())
                .create(UUID.fromString("b2c9e4d1-5a6f-4e7b-8c9d-0a1b2c3d4e5f"),
                        "job-" + jobId, ConfigurationRabbitMqNames.eventType("job", "finished"), data);

        assertSameAsExample(message, example);
    }

    private JsonNode example(String fileName) throws IOException {
        Path file = EXAMPLES.resolve(fileName);

        assertThat(file).as("el ejemplo %s tiene que estar versionado", fileName).exists();

        return objectMapper.readTree(Files.readString(file));
    }

    private AsynchronousMessageFactory factory(MessageActor actor, String correlationId) {
        MessageContextResolver contextResolver = mock(MessageContextResolver.class);
        when(contextResolver.currentActor()).thenReturn(actor);
        when(contextResolver.currentCorrelationId()).thenReturn(correlationId);

        AsynchronousMessageFactory factory = new AsynchronousMessageFactory(hashService, contextResolver);
        ReflectionTestUtils.setField(factory, "applicationName", "mto-configuration");

        return factory;
    }

    /**
     * Compara el mensaje con el ejemplo por el texto que viaja de verdad (releido como arbol para
     * que el orden de las claves no cuente), y la huella del ejemplo con la que se obtiene de sus
     * propias siete claves, que es la unica forma de que el ejemplo lleve una huella cierta.
     */
    private <T> void assertSameAsExample(AsynchronousMessage<T> message, JsonNode example) {
        ObjectNode produced = (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(Instant.parse(produced.get("creationDate").asText())).isNotNull();
        assertThat(produced.get("messageHash").asText()).matches("[0-9a-f]{64}");

        // La fecha es la de ahora y la huella la incluye: se toman las del ejemplo para comparar lo
        // demas, que es lo que el ejemplo fija.
        produced.put("creationDate", example.get("creationDate").asText());
        produced.put("messageHash", example.get("messageHash").asText());

        assertThat(produced).isEqualTo(example);

        AsynchronousMessage<T> asInExample = new AsynchronousMessage<>(
                message.operationId(),
                message.referenceId(),
                message.origin(),
                Instant.parse(example.get("creationDate").asText()),
                message.eventType(),
                message.data(),
                "PENDING",
                message.actor(),
                message.correlationId());

        assertThat(example.get("messageHash").asText())
                .as("la huella del ejemplo es la de sus siete claves originales, con la fecha del ejemplo")
                .isEqualTo(hashService.calculate(asInExample));
    }
}
