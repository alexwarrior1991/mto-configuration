package com.alejandro.mtoconfiguration.core.outbox;

import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Tests del relay contra PostgreSQL real: lo que se prueba aqui (FOR UPDATE SKIP
 * LOCKED, bloqueos entre transacciones, limites de columna) no lo reproduce ni un
 * mock ni H2.
 * <p>
 * La clase corre SIN transaccion de test: {@code @DataJpaTest} envuelve cada test en
 * una transaccion que hace rollback, y con eso los tests de concurrencia no verian
 * nada y el propio SKIP LOCKED seria indistinguible de una serializacion.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OutboxRelayIT.OutboxTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxRelayIT {

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxAdminService outboxAdminService;

    @Autowired
    private OutboxProperties outboxProperties;

    @Autowired
    private OutboxPublisherScheduler outboxPublisherScheduler;

    /** El broker no entra en estos tests: lo que se comprueba es lo que queda en la tabla. */
    @Autowired
    private OutboxRabbitPublisher outboxRabbitPublisher;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
    }

    @BeforeEach
    void resetState() {
        reset(outboxRabbitPublisher);
        outboxMessageRepository.deleteAll();

        outboxProperties.setBatchSize(50);
        outboxProperties.setMaxAttempts(20);
        outboxProperties.setInitialRetryDelay(Duration.ofSeconds(5));
        outboxProperties.setMaxRetryDelay(Duration.ofMinutes(5));
        outboxProperties.setRetryJitter(0d);
        outboxProperties.setClaimVisibilityTimeout(Duration.ofMinutes(5));
    }

    // ------------------------------------------------------------------ reclamo

    @Test
    void reclamarDejaElLoteEnProgresoEInvisibleParaElRestoDeReplicas() {
        List<UUID> ids = persistPending(3);

        List<OutboxRecord> primerLote = outboxRelayService.claimBatch();

        assertThat(primerLote).extracting(OutboxRecord::id).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(outboxRelayService.claimBatch())
                .as("un mensaje ya reclamado no puede volver a entregarse mientras dure su visibilidad")
                .isEmpty();

        assertThat(outboxMessageRepository.findAll())
                .allSatisfy(message -> {
                    assertThat(message.getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
                    assertThat(message.getNextAttemptAt()).isAfter(Instant.now());
                });
    }

    @Test
    void reclamarRespetaElTamanoDeLote() {
        persistPending(10);
        outboxProperties.setBatchSize(4);

        assertThat(outboxRelayService.claimBatch()).hasSize(4);
        assertThat(outboxRelayService.claimBatch()).hasSize(4);
        assertThat(outboxRelayService.claimBatch()).hasSize(2);
    }

    @Test
    void noSeReclamaLoQueTodaviaNoTocaReintentar() {
        persistPending(1);
        persist(message -> {
            message.setStatus(OutboxStatus.PENDING);
            message.setNextAttemptAt(Instant.now().plus(Duration.ofHours(1)));
        });

        assertThat(outboxRelayService.claimBatch()).hasSize(1);
    }

    @Test
    void unMensajeSinFechaDeReintentoNoSeQuedaAtascado() {
        // next_attempt_at admite nulos; con una comparacion directa esa fila jamas
        // entraria en el lote y se quedaria ahi para siempre.
        UUID id = persist(message -> {
            message.setStatus(OutboxStatus.PENDING);
            message.setNextAttemptAt(null);
        });

        assertThat(outboxRelayService.claimBatch()).extracting(OutboxRecord::id).containsExactly(id);
    }

    // --------------------------------------------------- exclusion entre replicas

    @Test
    void noSeReclamanFilasBloqueadasPorOtraTransaccionNiSeEsperaPorEllas() throws Exception {
        List<UUID> ids = persistPending(3);
        UUID bloqueado = ids.getFirst();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(
                    "select id from outbox_message where id = ? for update")) {
                statement.setObject(1, bloqueado);
                statement.execute();
            }

            // Sin SKIP LOCKED esta llamada se quedaria esperando al lock de arriba.
            List<OutboxRecord> lote = assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    () -> outboxRelayService.claimBatch(),
                    "claimBatch se ha bloqueado esperando una fila tomada por otra transaccion");

            assertThat(lote).extracting(OutboxRecord::id)
                    .containsExactlyInAnyOrderElementsOf(ids.subList(1, ids.size()))
                    .doesNotContain(bloqueado);

            connection.rollback();
        }
    }

    @Test
    void variasReplicasConcurrentesNoSeLlevanElMismoMensaje() throws Exception {
        int mensajes = 60;
        int replicas = 6;
        persistPending(mensajes);
        outboxProperties.setBatchSize(10);

        List<UUID> entregados = new CopyOnWriteArrayList<>();
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(replicas);
        List<Thread> hilos = new ArrayList<>();

        for (int i = 0; i < replicas; i++) {
            Thread hilo = new Thread(() -> {
                try {
                    salida.await();
                    outboxRelayService.claimBatch().forEach(record -> entregados.add(record.id()));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            });
            hilo.start();
            hilos.add(hilo);
        }

        salida.countDown();
        assertThat(fin.await(30, TimeUnit.SECONDS)).isTrue();
        for (Thread hilo : hilos) {
            hilo.join();
        }

        assertThat(entregados)
                .as("con varias replicas leyendo sin bloqueo, el mismo evento se publicaba N veces")
                .doesNotHaveDuplicates()
                .hasSize(replicas * 10);

        assertThat(Set.copyOf(entregados)).hasSize(replicas * 10);
    }

    @Test
    void unMensajeReclamadoSeRecuperaCuandoExpiraSuVisibilidad() throws Exception {
        outboxProperties.setClaimVisibilityTimeout(Duration.ofMillis(200));
        UUID id = persistPending(1).getFirst();

        List<OutboxRecord> primerReclamo = outboxRelayService.claimBatch();
        assertThat(primerReclamo).hasSize(1);

        // La replica que lo reclamo muere aqui, sin marcarlo ni publicado ni fallido.
        Thread.sleep(400);

        List<OutboxRecord> segundoReclamo = outboxRelayService.claimBatch();

        assertThat(segundoReclamo).extracting(OutboxRecord::id).containsExactly(id);
        assertThat(segundoReclamo.getFirst().attempts())
                .as("la recuperacion cuenta como intento para que no gire indefinidamente")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------ cierre

    @Test
    void marcarPublicadoDejaElMensajeCerrado() {
        UUID id = persistPending(1).getFirst();
        outboxRelayService.claimBatch();

        outboxRelayService.markPublished(id);

        OutboxMessage message = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(message.getPublishedAt()).isNotNull();
        assertThat(message.getNextAttemptAt()).isNull();
        assertThat(outboxRelayService.claimBatch()).isEmpty();
    }

    @Test
    void unErrorMasLargoQueLaColumnaNoRompeElRelay() {
        UUID id = persistPending(1).getFirst();
        outboxRelayService.claimBatch();
        String errorGigante = "detalle del fallo AMQP ".repeat(500);

        // Con el mensaje sin recortar, el flush reventaba y se perdia la tanda ENTERA,
        // incluidos los mensajes ya publicados; ademas el fallo se repetia cada pasada.
        assertThatCode(() -> outboxRelayService.markFailed(id, new IllegalStateException(errorGigante)))
                .doesNotThrowAnyException();

        OutboxMessage message = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(message.getLastError()).hasSizeLessThanOrEqualTo(OutboxErrors.MAX_LAST_ERROR_LENGTH);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void cadaFalloAplazaElSiguienteIntentoConBackoffExponencial() {
        UUID id = persistPending(1).getFirst();

        Instant antes = Instant.now();
        outboxRelayService.claimBatch();
        outboxRelayService.markFailed(id, new IllegalStateException("broker caido"));

        OutboxMessage primerFallo = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(primerFallo.getAttempts()).isEqualTo(1);
        assertThat(primerFallo.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(primerFallo.getNextAttemptAt()).isBetween(
                antes.plus(Duration.ofSeconds(4)), antes.plus(Duration.ofSeconds(7)));

        // Segundo fallo: el retardo se duplica.
        primerFallo.setNextAttemptAt(Instant.now().minusSeconds(1));
        outboxMessageRepository.save(primerFallo);

        Instant antesDelSegundo = Instant.now();
        outboxRelayService.claimBatch();
        outboxRelayService.markFailed(id, new IllegalStateException("broker caido"));

        OutboxMessage segundoFallo = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(segundoFallo.getAttempts()).isEqualTo(2);
        assertThat(segundoFallo.getNextAttemptAt()).isBetween(
                antesDelSegundo.plus(Duration.ofSeconds(9)), antesDelSegundo.plus(Duration.ofSeconds(12)));
    }

    @Test
    void alAgotarLosIntentosElMensajeQuedaEnFailed() {
        UUID id = persist(message -> {
            message.setStatus(OutboxStatus.PENDING);
            message.setNextAttemptAt(Instant.now().minusSeconds(1));
            message.setMaxAttempts(1);
        });
        outboxRelayService.claimBatch();

        outboxRelayService.markFailed(id, new IllegalStateException("nack"));

        OutboxMessage message = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getNextAttemptAt()).isNull();
        assertThat(outboxRelayService.claimBatch()).isEmpty();
    }

    // ------------------------------------------------------------------ redrive

    @Test
    void elRedriveDevuelveLosFailedAlCircuitoDePublicacion() {
        UUID id = persist(message -> {
            message.setStatus(OutboxStatus.FAILED);
            message.setAttempts(20);
            message.setLastError("agotado");
            message.setNextAttemptAt(null);
        });

        assertThat(outboxAdminService.redriveFailed(10)).isEqualTo(1);

        OutboxMessage message = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getAttempts()).isZero();
        assertThat(message.getLastError()).isNull();

        assertThat(outboxRelayService.claimBatch())
                .as("tras el redrive el mensaje debe volver a ser publicable")
                .extracting(OutboxRecord::id)
                .containsExactly(id);
    }

    @Test
    void elRedriveRespetaSuLimiteYNoTocaLoQueNoEstaEnFailed() {
        IntStream.range(0, 5).forEach(i -> persist(message -> message.setStatus(OutboxStatus.FAILED)));
        persistPending(2);

        assertThat(outboxAdminService.redriveFailed(3)).isEqualTo(3);
        assertThat(outboxMessageRepository.countByStatus(OutboxStatus.FAILED)).isEqualTo(2);
        assertThat(outboxMessageRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(5);
        assertThat(outboxAdminService.redriveFailed(0)).isZero();
    }

    @Test
    void lasEstadisticasReflejanElEstadoRealDelOutbox() {
        persistPending(2);
        persist(message -> message.setStatus(OutboxStatus.FAILED));
        persist(message -> message.setStatus(OutboxStatus.PUBLISHED));

        OutboxStats stats = outboxAdminService.stats();

        assertThat(stats.pending()).isEqualTo(2);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.published()).isEqualTo(1);
        assertThat(stats.inProgress()).isZero();
        assertThat(stats.oldestPendingCreatedAt())
                .as("la edad del PENDING mas antiguo es la senal de que el relay esta roto")
                .isNotNull();
    }

    // ------------------------------------------------------- relay de punta a punta

    @Test
    void elRelayCompletoDejaElMensajePublicadoCuandoElBrokerConfirma() {
        UUID id = persistPending(1).getFirst();

        outboxPublisherScheduler.publishPendingMessages();

        OutboxMessage message = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(message.getPublishedAt()).isNotNull();
    }

    @Test
    void elRelayCompletoAplazaElMensajeCuandoElBrokerNoConfirma() {
        UUID id = persistPending(1).getFirst();
        doThrow(new OutboxPublishException("nack del broker"))
                .when(outboxRabbitPublisher).publish(any());

        Instant antes = Instant.now();
        outboxPublisherScheduler.publishPendingMessages();

        OutboxMessage message = outboxMessageRepository.findById(id).orElseThrow();
        assertThat(message.getStatus())
                .as("sin ack del broker el mensaje NO puede quedar como publicado")
                .isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getLastError()).contains("nack del broker");
        assertThat(message.getNextAttemptAt()).isAfter(antes);

        // Y en la siguiente ventana vuelve a intentarse, no se pierde.
        message.setNextAttemptAt(Instant.now().minusSeconds(1));
        outboxMessageRepository.save(message);
        reset(outboxRabbitPublisher);

        outboxPublisherScheduler.publishPendingMessages();

        assertThat(outboxMessageRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
    }

    // ------------------------------------------------------------------ utilidades

    private List<UUID> persistPending(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> persist(message -> {
                    message.setStatus(OutboxStatus.PENDING);
                    message.setNextAttemptAt(Instant.now().minusSeconds(1));
                    message.setAggregateId(String.valueOf(i));
                }))
                .collect(Collectors.toList());
    }

    private UUID persist(java.util.function.Consumer<OutboxMessage> customizer) {
        Instant now = Instant.now();

        OutboxMessage message = new OutboxMessage();
        message.setId(UUID.randomUUID());
        message.setAggregateType("station");
        message.setAggregateId("1");
        message.setEventType("MASTER_DATA_STATION_CREATED");
        message.setExchangeName("mto.master-data.exchange");
        message.setRoutingKey("mto.master-data.station.created");
        message.setPayload("{\"id\":1}");
        message.setStatus(OutboxStatus.PENDING);
        message.setAttempts(0);
        message.setMaxAttempts(20);
        message.setCreatedAt(now);
        message.setNextAttemptAt(now.minusSeconds(1));

        customizer.accept(message);

        return outboxMessageRepository.save(message).getId();
    }

    @TestConfiguration
    @EnableConfigurationProperties(OutboxProperties.class)
    static class OutboxTestConfiguration {

        @Bean
        OutboxRetryPolicy outboxRetryPolicy(OutboxProperties outboxProperties) {
            return new OutboxRetryPolicy(outboxProperties);
        }

        @Bean
        OutboxRelayService outboxRelayService(OutboxMessageRepository repository,
                                              OutboxProperties properties,
                                              OutboxRetryPolicy retryPolicy) {
            return new OutboxRelayService(repository, properties, retryPolicy);
        }

        @Bean
        OutboxAdminService outboxAdminService(OutboxMessageRepository repository) {
            return new OutboxAdminService(repository);
        }

        @Bean
        OutboxRabbitPublisher outboxRabbitPublisher() {
            return org.mockito.Mockito.mock(OutboxRabbitPublisher.class);
        }

        @Bean
        OutboxPublisherScheduler outboxPublisherScheduler(OutboxRelayService relayService,
                                                          OutboxRabbitPublisher publisher) {
            return new OutboxPublisherScheduler(relayService, publisher);
        }
    }
}
