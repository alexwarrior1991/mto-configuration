package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.*;
import com.alejandro.mtoconfiguration.entity.lov.*;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.*;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.*;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Fija el contrato entre el {@code @EntityGraph} de cada {@code findByIdForMessaging}
 * y el {@code MasterDataEntityPayloadMapper} que lo consume: el grafo tiene que cargar
 * TODO lo que el mapper lee.
 * <p>
 * La comprobacion es detachar la entidad ({@code em.clear()}) entre la consulta y el
 * mapeo. Con la entidad desatachada, cualquier ruta que el grafo no haya traido y el
 * mapper si lea revienta con LazyInitializationException, mientras que leer el id de
 * un proxy sigue funcionando (acceso por propiedad), que es justo la distincion que
 * decide que rutas sobran en el grafo.
 * <p>
 * En produccion la entidad nunca se detacha: MasterDataEntityChangedEventListener es
 * un {@code @EventListener} plano y corre dentro de la transaccion de negocio, con la
 * sesion abierta. Por eso una ruta que falte HOY no da error, solo un select extra que
 * nadie ve. Sin este test, anadir un campo al mapper y olvidar el grafo es una
 * regresion de rendimiento invisible; con el, es un test en rojo.
 * <p>
 * {@link MessagingEntityGraphIT} es complementario: aquel valida que las rutas del
 * grafo existen (van en texto y el compilador no las mira), este que son suficientes.
 *
 * @see com.alejandro.mtoconfiguration.repository.MessagingEntityGraphIT
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MasterDataPayloadContractIT.AuditingTestConfiguration.class,
        MasterDataEventPayloadExtractor.class,
        CantileverMasterDataPayloadMapper.class,
        DisconnectorMasterDataPayloadMapper.class,
        ExecutionPackageMasterDataPayloadMapper.class,
        ProfileMasterDataPayloadMapper.class,
        SectionInsulatorMasterDataPayloadMapper.class,
        StationMasterDataPayloadMapper.class,
        SteadyArmMasterDataPayloadMapper.class,
        TrackMasterDataPayloadMapper.class
})
class MasterDataPayloadContractIT {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private MasterDataEventPayloadExtractor payloadExtractor;

    @Autowired
    private CantileverRepository cantileverRepository;
    @Autowired
    private DisconnectorRepository disconnectorRepository;
    @Autowired
    private ExecutionPackageRepository executionPackageRepository;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private SectionInsulatorRepository sectionInsulatorRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private SteadyArmRepository steadyArmRepository;
    @Autowired
    private TrackRepository trackRepository;

    private Ids ids;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @BeforeEach
    void persistFixture() {
        ids = new Fixture().build();
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Cantilever: el grafo basta para construir el payload desatachado")
    void cantilever() {
        Map<String, Object> payload = payloadOfDetached(cantileverRepository, ids.cantilever());

        assertThat(payload).containsKeys("profile", "cantileverType", "steadyArm");
    }

    @Test
    @DisplayName("Disconnector: el grafo basta para construir el payload desatachado")
    void disconnector() {
        Map<String, Object> payload = payloadOfDetached(disconnectorRepository, ids.disconnector());

        assertThat(payload).containsKeys("station", "profile", "disconnectorFunction");
    }

    @Test
    @DisplayName("ExecutionPackage: el grafo basta para construir el payload desatachado")
    void executionPackage() {
        Map<String, Object> payload = payloadOfDetached(executionPackageRepository, ids.executionPackage());

        assertThat(asList(payload, "tracks")).hasSize(2);
        assertThat(asList(payload, "stations")).hasSize(2);
    }

    @Test
    @DisplayName("Profile: el grafo basta para construir el payload desatachado")
    void profile() {
        Map<String, Object> payload = payloadOfDetached(profileRepository, ids.profile());

        assertThat(asList(payload, "cantilevers")).hasSize(2);
        assertThat(payload).containsKeys("track", "foundation", "poleType", "disconnector");
    }

    @Test
    @DisplayName("SectionInsulator: el grafo basta para construir el payload desatachado")
    void sectionInsulator() {
        Map<String, Object> payload = payloadOfDetached(sectionInsulatorRepository, ids.sectionInsulator());

        assertThat(payload).containsKey("station");
    }

    @Test
    @DisplayName("Station: el grafo basta para construir el payload desatachado")
    void station() {
        Map<String, Object> payload = payloadOfDetached(stationRepository, ids.station());

        assertThat(asList(payload, "tracks")).hasSize(2);
        assertThat(asList(payload, "disconnectors")).hasSize(2);
        assertThat(asList(payload, "sectionInsulators")).hasSize(2);
    }

    @Test
    @DisplayName("SteadyArm: el grafo basta para construir el payload desatachado")
    void steadyArm() {
        Map<String, Object> payload = payloadOfDetached(steadyArmRepository, ids.steadyArm());

        assertThat(payload).containsKeys("steadyArmType", "cantileverId");
        assertThat(payload.get("cantileverId")).isEqualTo(ids.cantilever());
    }

    @Test
    @DisplayName("Track: el grafo basta para construir el payload desatachado")
    void track() {
        Map<String, Object> payload = payloadOfDetached(trackRepository, ids.track());

        assertThat(asList(payload, "profiles")).hasSize(2);
        assertThat(payload).containsKeys("executionPackage", "station");
    }

    /**
     * Numero de sentencias SQL de cada consulta de mensajeria. Es el contador que
     * detecta lo que el mapeo desatachado NO puede ver: un {@code @OneToOne} del lado
     * inverso nunca es perezoso sin bytecode enhancement, asi que si falta en el grafo
     * Hibernate no lanza LazyInitializationException, lo carga con un select
     * secundario por fila. Un N+1 silencioso.
     * <p>
     * Que leer cada numero:
     * <ul>
     *   <li><b>1</b> es el objetivo: todo el grafo en una sentencia.</li>
     *   <li><b>station 3</b> y <b>executionPackage 2</b> son deliberados: leen tres y
     *       dos colecciones, y unirlas en un solo grafo multiplicaria las filas entre
     *       si. Si alguien las reagrupa, estos numeros bajan a 1 y el test falla.</li>
     *   <li><b>cantilever 2</b> y <b>track 3</b> NO son deliberados: son el N+1 de
     *       Profile.disconnector, que es el lado inverso de un {@code @OneToOne} y
     *       Hibernate carga con un select por cada Profile materializado. En track
     *       crece con el numero de perfiles de la via: 3 aqui con dos perfiles, 201
     *       con doscientos.</li>
     * </ul>
     */
    @Test
    @DisplayName("cada consulta de mensajeria se resuelve en un numero fijo de sentencias")
    void elNumeroDeSentenciasDeCadaConsultaEstaFijado() {
        assertSoftly(softly -> {
            softly.assertThat(countStatements(() -> cantileverRepository.findByIdForMessaging(ids.cantilever())))
                    .as("cantilever: 1 + el select de Profile.disconnector").isEqualTo(2);
            softly.assertThat(countStatements(() -> disconnectorRepository.findByIdForMessaging(ids.disconnector())))
                    .as("disconnector").isEqualTo(1);
            softly.assertThat(countStatements(() -> executionPackageRepository.findByIdForMessaging(ids.executionPackage())))
                    .as("executionPackage: una consulta por coleccion, sin cartesiano").isEqualTo(2);
            softly.assertThat(countStatements(() -> profileRepository.findByIdForMessaging(ids.profile())))
                    .as("profile").isEqualTo(1);
            softly.assertThat(countStatements(() -> sectionInsulatorRepository.findByIdForMessaging(ids.sectionInsulator())))
                    .as("sectionInsulator").isEqualTo(1);
            softly.assertThat(countStatements(() -> stationRepository.findByIdForMessaging(ids.station())))
                    .as("station: una consulta por coleccion, sin cartesiano").isEqualTo(3);
            softly.assertThat(countStatements(() -> steadyArmRepository.findByIdForMessaging(ids.steadyArm())))
                    .as("steadyArm").isEqualTo(1);
            softly.assertThat(countStatements(() -> trackRepository.findByIdForMessaging(ids.track())))
                    .as("track: 1 + un select de Profile.disconnector POR PERFIL").isEqualTo(3);
        });
    }

    /**
     * Carga la entidad por su consulta de mensajeria, la DESATACHA y solo entonces la
     * mapea: si al grafo le falta una ruta que el mapper lee, aqui salta
     * LazyInitializationException.
     */
    private <E extends IEntity> Map<String, Object> payloadOfDetached(
            MessagingEntityGraphRepository<E> repository, Long id) {

        E entity = repository.findByIdForMessaging(id).orElseThrow();
        em.clear();

        // Sin assertThat: una LazyInitializationException aqui YA es el fallo, y su
        // mensaje dice exactamente que relacion falta en el grafo.
        return payloadExtractor.extract(entity);
    }

    private long countStatements(Runnable action) {
        em.clear();

        Statistics statistics = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        long before = statistics.getPrepareStatementCount();

        action.run();

        return statistics.getPrepareStatementCount() - before;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Map<String, Object> payload, String key) {
        return (List<Map<String, Object>>) payload.get(key);
    }

    private record Ids(Long cantilever, Long disconnector, Long executionPackage, Long profile,
                       Long sectionInsulator, Long station, Long steadyArm, Long track) {
    }

    /**
     * Un unico arbol conectado que cubre a la vez a los ocho mappers. Las colecciones
     * llevan dos elementos a proposito: con uno solo, un producto cartesiano pasaria
     * desapercibido.
     */
    private final class Fixture {

        Ids build() {
            BusinessEntity company = company();

            ExecutionPackage executionPackage = new ExecutionPackage();
            executionPackage.setName("Paquete de pruebas");
            executionPackage.setInitialPackage(Boolean.TRUE);
            executionPackage.setLength(1_000L);
            executionPackage.setStartDate(LocalDate.of(2026, 1, 1));
            executionPackage.setEndDate(LocalDate.of(2026, 12, 31));
            executionPackage.setEnabled(true);
            executionPackage.setCompany(company);

            Station station = station("Estacion principal", executionPackage);
            station("Estacion secundaria", executionPackage);

            Track track = track("Via 1", executionPackage, station);
            track("Via 2", executionPackage, station);

            Profile profile = profile("P-001", "1.5", track);
            profile("P-002", "2.5", track);

            Cantilever cantilever = cantilever(profile, "5.500");
            cantilever(profile, "6.500");

            Disconnector disconnector = disconnector("Seccionador 1", station);
            profile.addDisconnector(disconnector);
            disconnector("Seccionador 2", station);

            em.persist(executionPackage);
            em.flush();

            SectionInsulator sectionInsulator = sectionInsulator("Aislador 1", station);
            sectionInsulator("Aislador 2", station);
            em.flush();

            return new Ids(
                    cantilever.getId(),
                    disconnector.getId(),
                    executionPackage.getId(),
                    profile.getId(),
                    sectionInsulator.getId(),
                    station.getId(),
                    cantilever.getSteadyArm().getId(),
                    track.getId()
            );
        }

        private BusinessEntity company() {
            BusinessEntity company = new BusinessEntity();
            company.setName("Constructora de pruebas");
            company.setCode("EMP1");
            company.setIdentificationNumber("B00000000");
            company.setComercialEntityType(lov(new ComercialEntityType(), "CET1"));
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
            profile.setKp(new BigDecimal(kp));
            profile.setAnchorage(lov(new Anchorage(), "ANC" + profileId.charAt(4)));
            profile.setAnchorageFoundation(lov(new AnchorageFoundation(), "ANF" + profileId.charAt(4)));
            profile.setFoundation(lov(new Foundation(), "FUN" + profileId.charAt(4)));
            profile.setPoleType(lov(new PoleType(), "POL" + profileId.charAt(4)));
            profile.setPortal(lov(new Portal(), "POR" + profileId.charAt(4)));
            profile.setProfileStatus(lov(new ProfileStatus(), "EST" + profileId.charAt(4)));
            profile.setReturnSupport(lov(new ReturnSupport(), "RET" + profileId.charAt(4)));
            profile.setSectioning(lov(new Sectioning(), "SEC" + profileId.charAt(4)));
            track.addProfile(profile);
            return profile;
        }

        private Cantilever cantilever(Profile profile, String cwHeight) {
            Cantilever cantilever = new Cantilever();
            cantilever.setCwHeight(new BigDecimal(cwHeight));
            cantilever.setCantileverType(lov(new CantileverType(), "MEN" + cwHeight.charAt(0)));
            addCantilever(profile, cantilever);

            SteadyArm steadyArm = new SteadyArm();
            steadyArm.setLength(1_200L);
            steadyArm.setSteadyArmType(lov(new SteadyArmType(), "ATI" + cwHeight.charAt(0)));
            cantilever.addSteadyArm(steadyArm);

            return cantilever;
        }

        private Disconnector disconnector(String name, Station station) {
            Disconnector disconnector = new Disconnector();
            disconnector.setName(name);
            disconnector.setOnLoad(Boolean.TRUE);
            disconnector.setDisconnectorFunction(
                    lov(new DisconnectorFunction(), "FUN" + name.charAt(name.length() - 1)));
            station.addDisconnector(disconnector);
            return disconnector;
        }

        private SectionInsulator sectionInsulator(String name, Station station) {
            SectionInsulator sectionInsulator = new SectionInsulator();
            sectionInsulator.setName(name);
            sectionInsulator.setEnabled(Boolean.TRUE);
            sectionInsulator.setStation(station);
            em.persist(sectionInsulator);
            return sectionInsulator;
        }

        /**
         * Cantilever y SectionInsulator son las dos unicas entidades de infrastructure
         * que NO redefinen equals, asi que heredan el de BaseEntity, que solo compara
         * el id. Con dos instancias aun sin persistir ambos ids son null y las dos se
         * consideran iguales, de modo que Profile.addCantilever y
         * Station.addSectionInsulator descartan la segunda en silencio.
         * <p>
         * El fixture necesita dos elementos por coleccion (con uno solo, un producto
         * cartesiano pasaria desapercibido), y cada caso se esquiva de una forma:
         * <ul>
         *   <li>Cantilever va en una {@code List} con {@code @OrderColumn}, asi que
         *       basta con saltarse la guarda {@code contains} y montar las dos puntas
         *       a mano; el orden lo sigue llevando la lista.</li>
         *   <li>SectionInsulator va en un {@code HashSet}: ahi no vale, porque con el
         *       id a null el segundo {@code add} tambien es un no-op sobre el propio
         *       set. Se persiste por el lado propietario (la FK STATION_ID) despues
         *       de que la estacion tenga id.</li>
         * </ul>
         * No se toca el modelo: eso es una decision aparte.
         */
        private void addCantilever(Profile profile, Cantilever cantilever) {
            profile.getCantilevers().add(cantilever);
            cantilever.setProfile(profile);
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
    static class AuditingTestConfiguration {

        /**
         * BaseEntity exige createUser y versionUser no nulos, y @DataJpaTest no carga
         * la configuracion de seguridad que los rellena en produccion.
         */
        @Bean
        AuditorAware<String> springSecurityAuditorAware() {
            return () -> Optional.of("test");
        }
    }
}
