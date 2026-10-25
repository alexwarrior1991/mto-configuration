package com.alejandro.mtoconfiguration.core.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.enums.infrastructure.DisconnectorDriveType;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
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
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.ProfileMasterDataPayloadMapper;
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
import java.util.LinkedHashSet;
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
 *
 * <p>Un evento nuevo o una clave nueva cambian el ejemplo en el mismo commit. Para regenerar los que
 * salen de un mapper de payload: {@code MESSAGING_EXAMPLES_WRITE=true ./mvnw test
 * -Dtest=MessagingContractExamplesTest}, y se revisa el diff como cualquier cambio de contrato.</p>
 */
class MessagingContractExamplesTest {

    private static final Path EXAMPLES = Path.of("docs", "messaging", "examples");

    private static final boolean WRITE = "true".equalsIgnoreCase(System.getenv("MESSAGING_EXAMPLES_WRITE"));

    /** La fecha de los ejemplos que se escriben; la de los que ya estaban es la suya. */
    private static final Instant CREATION_DATE = Instant.parse("2026-10-25T07:00:00Z");

    private static final MessageActor EDITOR = MessageActor.of("6f1b1c8e-0000-4000-8000-000000000001", "config.editor");

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

    /**
     * El perfil, con dos seccionadores en su poste (V29): {@code disconnectors} los trae todos, por
     * nombre, y {@code disconnector} sigue siendo el primero, para quien lo leia antes.
     */
    @Test
    @DisplayName("un perfil sale como el ejemplo, con sus dos seccionadores y el primero en disconnector")
    void elPerfilSaleComoElEjemplo() throws IOException {
        Profile profile = ExampleEntities.profileWithTwoDisconnectors();

        check("master-data-profile-updated.json", "9e1f2a3b-4c5d-4e6f-8a7b-0c1d2e3f4a5b", "profile",
                profile.getId(), new ProfileMasterDataPayloadMapper().toPayload(profile));

        JsonNode example = example("master-data-profile-updated.json");
        assertThat(example.at("/data/values/disconnectors").findValuesAsString("name"))
                .containsExactly("NAT-NS3", "NAT-NS4");
        assertThat(example.at("/data/values/disconnector/name").asText()).isEqualTo("NAT-NS3");
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

    /**
     * Un evento de datos maestros con los {@code values} de su mapper real, comparado con su ejemplo
     * (o escrito, con {@code MESSAGING_EXAMPLES_WRITE=true}).
     */
    private void check(String fileName, String operationId, String entityName, Long id, Map<String, Object> values)
            throws IOException {
        MasterDataChangedEvent data = new MasterDataChangedEvent(entityName, String.valueOf(id),
                MasterDataOperation.UPDATED, values);
        AsynchronousMessage<MasterDataChangedEvent> message = factory(EDITOR, "5d2e7f10-2222-4333-8444-" + "%012d".formatted(id))
                .create(UUID.fromString(operationId), entityName + "-" + id,
                        MasterDataEventTypeBuilder.build(entityName, MasterDataOperation.UPDATED), data);

        if (WRITE) {
            write(message, fileName);
        }
        assertSameAsExample(message, example(fileName));
    }

    private <T> void write(AsynchronousMessage<T> message, String fileName) throws IOException {
        AsynchronousMessage<T> dated = new AsynchronousMessage<>(
                message.operationId(),
                message.referenceId(),
                message.origin(),
                CREATION_DATE,
                message.eventType(),
                message.data(),
                "PENDING",
                message.actor(),
                message.correlationId());
        AsynchronousMessage<T> hashed = dated.withMessageHash(hashService.calculate(dated));
        // Del mensaje y no de un arbol releido: asi los decimales salen con su escala, como viajan
        // (110176.000, no 110176.0). Los separadores, como en los ejemplos escritos a mano.
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(hashed)
                .replace("\" : ", "\": ")
                .replace("[ ]", "[]");
        Files.writeString(EXAMPLES.resolve(fileName), json + "\n");
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

    /**
     * Las entidades de los ejemplos, con ids y nombres del plano (EP5, el poste 68-2.05 con sus dos
     * seccionadores de la zona neutra de NAT): lo que un consumidor copia como fixture tiene que
     * parecerse a lo que le llega.
     */
    static final class ExampleEntities {

        private ExampleEntities() {
        }

        static ExecutionPackage executionPackage() {
            ExecutionPackage executionPackage = new ExecutionPackage();
            executionPackage.setId(5L);
            executionPackage.setName("EP5");
            executionPackage.setInitialPackage(false);
            executionPackage.setEnabled(true);
            return executionPackage;
        }

        static Station station(Long id, String name, ExecutionPackage executionPackage) {
            Station station = new Station();
            station.setId(id);
            station.setName(name);
            station.setExecutionPackage(executionPackage);
            return station;
        }

        static Track track(Long id, String name, ExecutionPackage executionPackage) {
            Track track = new Track();
            track.setId(id);
            track.setName(name);
            track.setEnabled(true);
            track.setExecutionPackage(executionPackage);
            return track;
        }

        static <L extends Lov> L lov(L lov, Long id, String code) {
            lov.setId(id);
            lov.setCode(code);
            lov.setEnabled(true);
            return lov;
        }

        static Profile profileWithTwoDisconnectors() {
            ExecutionPackage executionPackage = executionPackage();
            Station nat = station(51L, "NAT", executionPackage);
            Track track = track(52L, "TRACK 2", executionPackage);
            nat.addTrack(track);

            Profile profile = new Profile();
            profile.setId(6802L);
            profile.setProfileId("68-2.05");
            profile.setKp(new BigDecimal("68205.000"));
            profile.setOrderInTrack(5);
            profile.setSpan(new BigDecimal("55.000"));
            profile.setRailPoleDistance(new BigDecimal("-3100"));
            profile.setTrack(track);
            profile.setPoleType(lov(new PoleType(), 2L, "HEB"));
            profile.setProfileStatus(lov(new ProfileStatus(), 1L, "OK"));
            profile.setSectionings(new LinkedHashSet<>(java.util.List.of(lov(new Sectioning(), 31L, "NS"))));

            Cantilever cantilever = new Cantilever();
            cantilever.setId(9001L);
            cantilever.setCwHeight(new BigDecimal("5.300"));
            cantilever.setStagger(new BigDecimal("200"));
            cantilever.setCantileverType(lov(new CantileverType(), 11L, "PT1"));
            profile.addCantilever(cantilever);

            DisconnectorFunction function = lov(new DisconnectorFunction(), 21L, "Disc/IO");
            // Se cuelgan al reves de su nombre a proposito: el evento los ordena.
            profile.addDisconnector(disconnector(702L, "NAT-NS4", false, nat, function));
            profile.addDisconnector(disconnector(701L, "NAT-NS3", true, nat, function));
            return profile;
        }

        static Disconnector disconnector(Long id, String name, Boolean normallyOpen, Station station,
                                         DisconnectorFunction function) {
            Disconnector disconnector = new Disconnector();
            disconnector.setId(id);
            disconnector.setName(name);
            disconnector.setOnLoad(true);
            disconnector.setNormallyOpen(normallyOpen);
            disconnector.setDriveType(DisconnectorDriveType.MOTOR);
            disconnector.setStation(station);
            disconnector.setDisconnectorFunction(function);
            return disconnector;
        }
    }
}
