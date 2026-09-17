package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessageFactory;
import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessageHashService;
import com.alejandro.mtoconfiguration.core.outbox.NoOpOutboxTracing;
import com.alejandro.mtoconfiguration.core.outbox.OutboxMessage;
import com.alejandro.mtoconfiguration.core.outbox.OutboxMessageRepository;
import com.alejandro.mtoconfiguration.core.outbox.OutboxProperties;
import com.alejandro.mtoconfiguration.core.outbox.OutboxService;
import com.alejandro.mtoconfiguration.core.outbox.OutboxStatus;
import com.alejandro.mtoconfiguration.core.outbox.OutboxTracing;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.ComercialEntityType;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.enums.jobs.MasterDataRepublishTarget;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityIdResolver;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityNameResolver;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEventPayloadExtractor;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEventPublisher;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataRabbitMqNames;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.DisconnectorMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.ProfileMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.SectionInsulatorMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El republicado, de punta a punta contra PostgreSQL.
 *
 * <p>Esto es lo que de verdad hay que probar: que lo que acaba en {@code outbox_message} es
 * <b>indistinguible</b> de lo que escribe una edicion real. Los tests con dobles comprueban que se
 * llama al publicador; solo aqui se ve el agregado, el tipo de evento, la clave de enrutado y el
 * payload que van a llegarle al consumidor —y el {@code sequence_number} que le asigna la base, que
 * es la marca de agua con la que ese consumidor decide si aplica el mensaje o lo descarta.</p>
 *
 * <p>El {@code sequence_number} es ademas el motivo de que esto tenga que correr contra la base de
 * verdad: lo pone un {@code DEFAULT nextval(...)} que vive en la migracion, asi que con un esquema
 * generado por Hibernate no existiria.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MasterDataRepublishIT.RepublishTestConfiguration.class,
        MasterDataEventPayloadExtractor.class,
        MasterDataEntityNameResolver.class,
        MasterDataEntityIdResolver.class,
        MasterDataEventPublisher.class,
        MasterDataRepublishBatchPublisher.class,
        MasterDataRepublishJobRunner.class,
        DisconnectorMasterDataPayloadMapper.class,
        ProfileMasterDataPayloadMapper.class,
        SectionInsulatorMasterDataPayloadMapper.class
})
class MasterDataRepublishIT {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;
    @Autowired
    private MasterDataRepublishJobRunner runner;
    @Autowired
    private MasterDataRepublishBatchPublisher batchPublisher;
    @Autowired
    private MasterDataEventPayloadExtractor payloadExtractor;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private AsyncJobProperties properties;

    private Ids ids;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
    }

    private record Ids(Long trackId, Long stationId, Long profileId) {
    }

    @BeforeEach
    void persistFixture() {
        outboxMessageRepository.deleteAll();
        ids = new Fixture().build();
        em.flush();
        em.clear();
    }

    private void republish(MasterDataRepublishTarget target, Long trackId, Long stationId) {
        runner.run(JOB_ID, target, trackId, stationId,
                new ProfileJobProgress(null, properties.getProfile(), p -> { }));
        em.flush();
    }

    private List<OutboxMessage> outboxOf(String aggregateType) {
        return outboxMessageRepository.findAll().stream()
                .filter(message -> aggregateType.equals(message.getAggregateType()))
                .sorted(Comparator.comparing(OutboxMessage::getSequenceNumber))
                .toList();
    }

    @Test
    @DisplayName("deja una fila de outbox por perfil, como UPDATED y lista para publicar")
    void unaFilaPorPerfil() {
        republish(MasterDataRepublishTarget.PROFILE, null, null);

        List<OutboxMessage> messages = outboxOf("profile");

        assertThat(messages).hasSize(3);
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getEventType()).isEqualTo("MASTER_DATA_PROFILE_UPDATED");
            assertThat(message.getExchangeName()).isEqualTo(MasterDataRabbitMqNames.MASTER_DATA_EXCHANGE);
            assertThat(message.getRoutingKey()).isEqualTo("mto.master-data.profile.updated");
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);

            // Lo asigna la base con un DEFAULT nextval que solo existe en la migracion. Es la marca
            // de agua con la que el consumidor descarta lo que llega tarde.
            assertThat(message.getSequenceNumber()).isNotNull();
        });
    }

    @Test
    @DisplayName("el payload republicado es identico al de una edicion real")
    void elPayloadEsElDeUnaEdicionReal() {
        republish(MasterDataRepublishTarget.PROFILE, ids.trackId(), null);
        em.clear();

        // El payload que produciria un cambio de verdad: misma relectura con el grafo de mensajeria
        // y mismo extractor. Si el republicado tomara otro camino —findById, o la entidad tal
        // cual— el consumidor recibiria algo distinto segun de donde viniera el evento.
        Profile expected = profileRepository.findByIdForMessaging(ids.profileId()).orElseThrow();
        Map<String, Object> expectedValues = payloadExtractor.extract(expected);
        String expectedJson = new ObjectMapper().writeValueAsString(expectedValues);

        OutboxMessage message = outboxOf("profile").stream()
                .filter(m -> String.valueOf(ids.profileId()).equals(m.getAggregateId()))
                .findFirst()
                .orElseThrow();

        // Se compara el TEXTO y no dos mapas deserializados a proposito: la ida y vuelta por JSON
        // no es la identidad —un BigDecimal 1.50 vuelve como 1.5— y dos payloads identicos podrian
        // salir distintos por eso. Es el mismo motivo por el que el messageHash del sobre no sirve
        // para verificar nada (README_MESSAGING, 13.1).
        assertThat(message.getPayload()).contains("\"values\":" + expectedJson);
    }

    @Test
    @DisplayName("el filtro por via deja fuera los perfiles de las demas")
    void elFiltroPorViaAcota() {
        republish(MasterDataRepublishTarget.PROFILE, ids.trackId(), null);

        assertThat(outboxOf("profile")).hasSize(2);
    }

    @Test
    @DisplayName("el filtro por estacion acota seccionadores y aisladores")
    void elFiltroPorEstacionAcota() {
        republish(MasterDataRepublishTarget.DISCONNECTOR, null, ids.stationId());
        republish(MasterDataRepublishTarget.SECTION_INSULATOR, null, ids.stationId());

        assertThat(outboxOf("disconnector")).hasSize(1);
        assertThat(outboxOf("section-insulator")).hasSize(1);
    }

    @Test
    @DisplayName("all republica los tres tipos con su nombre de agregado")
    void allRepublicaLosTresTipos() {
        republish(MasterDataRepublishTarget.ALL, null, null);

        assertThat(outboxOf("profile")).hasSize(3);
        assertThat(outboxOf("disconnector")).hasSize(2);
        assertThat(outboxOf("section-insulator")).hasSize(2);
    }

    @Test
    @DisplayName("recorre por paginas sin dejarse ninguno ni repetir")
    void recorrePorPaginasSinDejarseNinguno() {
        properties.getRepublish().setBatchSize(2);

        republish(MasterDataRepublishTarget.PROFILE, null, null);

        // Tres perfiles con lotes de dos: dos paginas y una vacia. Un keyset que no avanzara se
        // quedaria repitiendo la primera pagina, y uno que avanzara de mas se saltaria el tercero.
        assertThat(outboxOf("profile"))
                .extracting(OutboxMessage::getAggregateId)
                .doesNotHaveDuplicates()
                .hasSize(3);
    }

    @Test
    @DisplayName("el lote corre dentro de una transaccion abierta por el aspecto, no suelto")
    void elLoteCorreEnUnaTransaccionDelAspecto() {
        List<Long> pagina = profileRepository.findIdsForRepublish(ids.trackId(), 0L, Pageable.ofSize(10));
        assertThat(pagina).hasSizeGreaterThan(1);

        AtomicBoolean conTransaccionDelAspecto = new AtomicBoolean();

        batchPublisher.publishBatch(pagina, "profile", id -> {
            conTransaccionDelAspecto.set(hayTransaccionDelAspecto());
            return profileRepository.findByIdForMessaging(id);
        }, 0);

        // La comprobacion que ninguna otra prueba puede hacer: que el @Transactional de
        // publishBatch se APLICA. Si no se aplicara —una visibilidad que el proxy no advierte, un
        // import equivocado— no fallaria nada visible: cada lectura y cada escritura irian en su
        // propia transaccion implicita y el lote dejaria de ser atomico, que es justo lo que este
        // diseño existe para impedir.
        //
        // Sirve como comprobacion porque la transaccion del test NO la gestiona el aspecto, sino
        // el TransactionalTestExecutionListener: fuera de publishBatch esta misma llamada lanza
        // NoTransactionException. Que aqui dentro haya un TransactionStatus solo puede venir del
        // interceptor de transacciones.
        assertThat(conTransaccionDelAspecto)
                .as("el lote comparte una transaccion abierta por el interceptor de transacciones")
                .isTrue();
        assertThat(hayTransaccionDelAspecto())
                .as("y fuera del lote no hay ninguna, que es lo que hace concluyente la de dentro")
                .isFalse();
    }

    private static boolean hayTransaccionDelAspecto() {
        try {
            TransactionAspectSupport.currentTransactionStatus();
            return true;
        } catch (NoTransactionException e) {
            return false;
        }
    }

    @Test
    @DisplayName("es reejecutable: repetirlo vuelve a publicar con numeros de secuencia mayores")
    void esReejecutable() {
        republish(MasterDataRepublishTarget.PROFILE, ids.trackId(), null);

        List<OutboxMessage> primera = outboxOf("profile");
        long ultimoDeLaPrimera = primera.getLast().getSequenceNumber();

        republish(MasterDataRepublishTarget.PROFILE, ids.trackId(), null);

        List<OutboxMessage> todas = outboxOf("profile");

        assertThat(todas).hasSize(primera.size() * 2);

        // Mayores, y esa es toda la garantia que necesita el consumidor: su upsert es idempotente
        // por (source_service, source_entity_id) y su marca de agua acepta lo que sube. Por eso
        // relanzar el republicado es inocuo en destino.
        assertThat(todas.stream().skip(primera.size()).map(OutboxMessage::getSequenceNumber))
                .allSatisfy(sequence -> assertThat(sequence).isGreaterThan(ultimoDeLaPrimera));
    }

    @Test
    @DisplayName("un perfil borrado no se republica como si siguiera vivo")
    void elBorradoLogicoQuedaFuera() {
        Profile profile = em.find(Profile.class, ids.profileId());
        profile.delete();
        em.flush();
        em.clear();

        republish(MasterDataRepublishTarget.PROFILE, ids.trackId(), null);

        // El @SQLRestriction de CRUDEntity lo deja fuera de la consulta de ids: republicarlo como
        // UPDATED resucitaria en el consumidor algo que aqui esta borrado.
        assertThat(outboxOf("profile"))
                .extracting(OutboxMessage::getAggregateId)
                .doesNotContain(String.valueOf(ids.profileId()))
                .hasSize(1);
    }

    private final class Fixture {

        Ids build() {
            BusinessEntity company = company();

            ExecutionPackage executionPackage = new ExecutionPackage();
            executionPackage.setName("Paquete de republicado");
            executionPackage.setInitialPackage(Boolean.TRUE);
            executionPackage.setLength(1_000L);
            executionPackage.setStartDate(LocalDate.of(2026, 1, 1));
            executionPackage.setEndDate(LocalDate.of(2026, 12, 31));
            executionPackage.setEnabled(true);
            executionPackage.setCompany(company);

            Station station = station("Estacion A", executionPackage);
            Station otherStation = station("Estacion B", executionPackage);

            Track track = track("Via 1", executionPackage, station);
            Track otherTrack = track("Via 2", executionPackage, otherStation);

            Profile profile = profile("P-001", "1.50", track);
            profile("P-002", "2.50", track);
            profile("P-003", "3.50", otherTrack);

            disconnector("Seccionador A", station);
            disconnector("Seccionador B", otherStation);

            sectionInsulator("Aislador A", station);
            sectionInsulator("Aislador B", otherStation);

            em.persist(executionPackage);
            em.flush();

            return new Ids(track.getId(), station.getId(), profile.getId());
        }

        private BusinessEntity company() {
            BusinessEntity company = new BusinessEntity();
            company.setName("Constructora de republicado");
            company.setCode("EMPR");
            company.setIdentificationNumber("B00000001");
            company.setComercialEntityType(lov(new ComercialEntityType(), "CETR"));
            em.persist(company);
            return company;
        }

        private Station station(String name, ExecutionPackage executionPackage) {
            Station station = new Station();
            station.setName(name);
            executionPackage.addStation(station);
            return station;
        }

        private Track track(String name, ExecutionPackage executionPackage, Station station) {
            Track track = new Track();
            track.setName(name);
            track.setEnabled(Boolean.TRUE);
            executionPackage.addTrack(track);
            station.addTrack(track);
            return track;
        }

        private Profile profile(String profileId, String kp, Track track) {
            Profile profile = new Profile();
            profile.setProfileId(profileId);
            // Con decimales y escala: es justo el dato que la ida y vuelta por JSON no conserva,
            // asi que un payload comparado mal aqui se nota.
            profile.setKp(new BigDecimal(kp));
            track.addProfile(profile);
            return profile;
        }

        private Disconnector disconnector(String name, Station station) {
            Disconnector disconnector = new Disconnector();
            disconnector.setName(name);
            disconnector.setOnLoad(Boolean.TRUE);
            disconnector.setDisconnectorFunction(
                    lov(new DisconnectorFunction(), "FR" + name.charAt(name.length() - 1)));
            station.addDisconnector(disconnector);
            return disconnector;
        }

        private SectionInsulator sectionInsulator(String name, Station station) {
            SectionInsulator sectionInsulator = new SectionInsulator();
            sectionInsulator.setName(name);
            sectionInsulator.setEnabled(Boolean.TRUE);
            station.addSectionInsulator(sectionInsulator);
            return sectionInsulator;
        }

        private <L extends Lov> L lov(L lov, String code) {
            lov.setCode(code);
            lov.setDescription("Descripcion de " + code);
            lov.setEnabled(true);
            em.persist(lov);
            return lov;
        }
    }

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
    static class RepublishTestConfiguration {

        /**
         * BaseEntity exige createUser y versionUser no nulos, y @DataJpaTest no carga la
         * configuracion de seguridad que los rellena en produccion.
         */
        @Bean
        AuditorAware<String> springSecurityAuditorAware() {
            return () -> Optional.of("test");
        }

        @Bean
        AsyncJobProperties asyncJobProperties() {
            return new AsyncJobProperties();
        }

        @Bean
        OutboxProperties outboxProperties() {
            return new OutboxProperties();
        }

        @Bean
        OutboxTracing outboxTracing() {
            return new NoOpOutboxTracing();
        }

        @Bean
        AsynchronousMessageHashService asynchronousMessageHashService() {
            return new AsynchronousMessageHashService(new ObjectMapper());
        }

        @Bean
        AsynchronousMessageFactory asynchronousMessageFactory(AsynchronousMessageHashService hashService) {
            return new AsynchronousMessageFactory(hashService);
        }

        @Bean
        OutboxService outboxService(OutboxMessageRepository repository,
                                    OutboxProperties properties,
                                    OutboxTracing tracing,
                                    ApplicationEventPublisher eventPublisher) {
            return new OutboxService(repository, properties, new ObjectMapper(), tracing, eventPublisher);
        }
    }
}
