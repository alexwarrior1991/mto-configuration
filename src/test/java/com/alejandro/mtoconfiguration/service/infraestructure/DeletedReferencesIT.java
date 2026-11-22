package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.masterdata.messaging.mapper.DisconnectorMasterDataPayloadMapper;
import com.alejandro.mtoconfiguration.model.audit.EntityRevisionDTO;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Lo que queda apuntando a un maestro borrado.
 *
 * <p>El {@code DELETE} directo de un maestro es logico y no toca lo que cuelga de el (README_API.md
 * §4): borrar una via deja sus perfiles, sus aisladores y sus puntos de acceso apuntando a ella;
 * borrar una estacion, sus seccionadores y el enlace con sus vias; borrar un poste, sus
 * seccionadores; borrar un paquete, sus vias y sus estaciones. La fila borrada deja de existir para
 * las consultas ({@code @SQLRestriction("deleted = false")}), pero la clave ajena sigue ahi, y eso
 * rompia cuatro cosas:
 *
 * <ul>
 *   <li>una via seguia listando la estacion borrada (en su detalle, en su fila, en su esquema y en
 *       sus eventos), y devolverla tal como se leyo era un 404: el lado dueno de
 *       {@code Track.stations} no repetia la restriccion;</li>
 *   <li>un seccionador cuyo poste se borro no se podia leer, y con el fallaba la lista entera de
 *       seccionadores;</li>
 *   <li>modificar cualquier fila que apuntase a un maestro borrado era un 500: el grafo de
 *       mensajeria unia la referencia con su {@code deleted = false} y Hibernate rellenaba con
 *       nulos el proxy que ya tenia;</li>
 *   <li>y no se podia borrar: el evento {@code DELETED} nombraba al maestro borrado.</li>
 * </ul>
 *
 * <p>Ahora se lee (lo que es del maestro borrado va a {@code null}), se borra (su evento lleva esa
 * referencia a {@code null}) y modificarla sin apuntarla a un maestro vivo es el 400
 * {@code BUS-001} sobre su campo que README_API.md §9 da a una referencia que no existe. Los datos
 * van por SQL y lo que se borra, por su servicio, que es el {@code DELETE} de verdad.
 */
@SpringBootTest
@DisplayName("Lo que queda apuntando a un maestro borrado")
class DeletedReferencesIT {

    private static final long LOW = -9_799_999L;
    private static final long HIGH = -9_700_000L;
    private static final long FUNCTION = -9_700_001L;
    private static final long CANTILEVER_TYPE = -9_700_002L;
    private static final long ARM_TYPE = -9_700_003L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private TrackService trackService;
    @Autowired
    private StationService stationService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private CantileverService cantileverService;
    @Autowired
    private SteadyArmService steadyArmService;
    @Autowired
    private DisconnectorService disconnectorService;
    @Autowired
    private SectionInsulatorService sectionInsulatorService;
    @Autowired
    private TrackAccessPointService trackAccessPointService;
    @Autowired
    private ExecutionPackageService executionPackageService;
    @Autowired
    private TrackSchematicService trackSchematicService;
    @Autowired
    private DisconnectorRepository disconnectorRepository;
    @Autowired
    private DisconnectorMasterDataPayloadMapper disconnectorPayloadMapper;

    /** Cada caso toma su bloque de ids, para no pisar al anterior. */
    private long next = HIGH - 100;

    @BeforeEach
    void catalogos() {
        limpia();
        lov("disconnector_function", FUNCTION);
        lov("cantilever_type", CANTILEVER_TYPE);
        lov("steady_arm_type", ARM_TYPE);
    }

    @AfterEach
    void limpia() {
        jdbc.update("delete from outbox_message where payload like '%IT-DELREF%'");
        for (String table : List.of("steady_arm", "cantilever", "disconnector", "section_insulator",
                "track_access_point", "profile")) {
            borra(table);
        }
        for (String table : List.of("track_station", "track_station_aud")) {
            jdbc.update("delete from " + table + " where track_id between ? and ?", LOW, HIGH);
        }
        for (String table : List.of("track", "station", "execution_package", "disconnector_function",
                "cantilever_type", "steady_arm_type")) {
            borra(table);
        }
    }

    private void borra(String table) {
        jdbc.update("delete from " + table + " where id between ? and ?", LOW, HIGH);
        jdbc.update("delete from " + table + "_aud where id between ? and ?", LOW, HIGH);
    }

    // --- Una via y la estacion borrada ---------------------------------------------------

    @Test
    @DisplayName("una via deja de listar la estacion que se borro, en su detalle, su fila, su esquema y su evento, y se puede devolver tal como se leyo")
    void laViaDejaDeListarLaEstacionBorrada() {
        long e = block();
        paquete(e);
        estacion(e - 1, e);
        estacion(e - 2, e);
        via(e - 3, e);
        enlace(e - 3, e - 1);
        enlace(e - 3, e - 2);

        stationService.delete(stationService.getById(e - 2));

        TrackDTO leida = trackService.getById(e - 3);
        assertThat(leida.getStationIds()).containsExactly(e - 1);
        assertThat(trackService.findAll(PageRequest.of(0, 500)).getContent())
                .filteredOn(each -> each.getId().equals(e - 3)).singleElement()
                .satisfies(fila -> assertThat(fila.getStationIds()).containsExactly(e - 1));
        assertThat(trackSchematicService.getSchematic(e - 3).toString())
                .contains(nombre("ST", e - 1)).doesNotContain(nombre("ST", e - 2));

        leida.setName(nombre("T", e - 3) + "-BIS");
        TrackDTO guardada = trackService.update(leida);

        assertThat(guardada.getStationIds()).containsExactly(e - 1);
        assertThat(ultimoEvento(e - 3, "UPDATED")).contains(nombre("ST", e - 1)).doesNotContain(nombre("ST", e - 2));
        assertThat(jdbc.queryForObject("select count(*) from track_station where track_id = ?", Integer.class, e - 3))
                .as("el enlace con la estacion borrada se queda: el borrado no toca lo que cuelga").isEqualTo(2);
    }

    @Test
    @DisplayName("el historial de una via tampoco nombra la estacion borrada desde que se borro")
    void elHistorialDeLaViaTampoco() {
        long e = block();
        paquete(e);
        StationDTO una = stationService.create(estacionDto(nombre("ST", e - 1), e));
        StationDTO otra = stationService.create(estacionDto(nombre("ST", e - 2), e));
        TrackDTO via = new TrackDTO();
        via.setName(nombre("T", e - 3));
        via.setEnabled(true);
        via.setExecutionPackageId(e);
        via.setStationIds(new ArrayList<>(List.of(una.getId(), otra.getId())));
        via = trackService.create(via);
        long viaId = via.getId();
        try {
            stationService.delete(stationService.getById(otra.getId()));
            TrackDTO leida = trackService.getById(viaId);
            leida.setName(nombre("T", e - 3) + "-BIS");
            trackService.update(leida);

            List<EntityRevisionDTO<TrackDTO>> revisiones =
                    trackService.findRevisions(viaId, PageRequest.of(0, 20)).getContent();

            assertThat(revisiones.getLast().entity().getStationIds())
                    .containsExactlyInAnyOrder(una.getId(), otra.getId());
            assertThat(revisiones.getFirst().entity().getName()).endsWith("-BIS");
            assertThat(revisiones.getFirst().entity().getStationIds()).containsExactly(una.getId());
        } finally {
            jdbc.update("delete from outbox_message where payload like '%IT-DELREF%'");
            for (String table : List.of("track_station", "track_station_aud")) {
                jdbc.update("delete from " + table + " where track_id = ?", viaId);
            }
            jdbc.update("delete from track where id = ?", viaId);
            jdbc.update("delete from track_aud where id = ?", viaId);
            jdbc.update("delete from station where id in (?, ?)", una.getId(), otra.getId());
            jdbc.update("delete from station_aud where id in (?, ?)", una.getId(), otra.getId());
        }
    }

    // --- El seccionador cuyo poste se borro ----------------------------------------------

    @Test
    @DisplayName("un seccionador cuyo poste se borro se sigue leyendo, solo y en la lista, sin el codigo ni el KP del poste")
    void elSeccionadorDeUnPosteBorradoSeLee() {
        long e = block();
        paquete(e);
        estacion(e - 1, e);
        via(e - 2, e);
        perfil(e - 3, e - 2);
        seccionador(e - 4, e - 1, e - 3, null, null);
        assertThat(disconnectorService.getById(e - 4).getProfileCode()).isEqualTo(nombre("P", e - 3));

        profileService.delete(profileService.getById(e - 3));

        DisconnectorDTO leido = disconnectorService.getById(e - 4);
        assertThat(leido.getProfileId()).as("a cual apuntaba, como cualquier otra referencia").isEqualTo(e - 3);
        assertThat(leido.getProfileCode()).isNull();
        assertThat(leido.getProfileKp()).isNull();
        assertThat(disconnectorService.findAll(PageRequest.of(0, 500)).getContent())
                .extracting(DisconnectorDTO::getId).contains(e - 4);
    }

    @Test
    @DisplayName("el grafo de mensajeria de un seccionador cuya estacion se borro se carga, y su evento lleva la estacion a null")
    void elGrafoDeMensajeriaConUnaReferenciaBorrada() {
        long e = block();
        paquete(e);
        estacion(e - 1, e);
        via(e - 2, e);
        seccionador(e - 4, e - 1, null, e - 2, null);
        stationService.delete(stationService.getById(e - 1));

        Map<String, Object> payload = transactions.execute(status -> {
            Disconnector entity = disconnectorRepository.findByIdForMessaging(e - 4).orElseThrow();
            return disconnectorPayloadMapper.toPayload(entity);
        });

        assertThat(payload).containsEntry("station", null);
        assertThat(payload.get("track")).isNotNull();
    }

    // --- Modificar y borrar lo que apunta a un maestro borrado ----------------------------

    @TestFactory
    @DisplayName("modificar una fila que apunta a un maestro borrado es un 400 BUS-001 sobre su campo, y se puede borrar")
    Stream<DynamicTest> modificarYBorrar() {
        Map<String, Caso> casos = new LinkedHashMap<>();
        casos.put("un perfil cuya via se borro", perfilDeViaBorrada());
        casos.put("una mensula cuyo perfil se borro", mensulaDePerfilBorrado());
        casos.put("un brazo cuya mensula se borro", brazoDeMensulaBorrada());
        casos.put("un seccionador cuya estacion se borro", seccionadorDe("stationId"));
        casos.put("un seccionador cuyo poste se borro", seccionadorDe("profileId"));
        casos.put("un seccionador cuya via propia se borro", seccionadorDe("trackId"));
        casos.put("un seccionador cuya via conectada se borro", seccionadorDe("connectedTrackId"));
        casos.put("un aislador cuya estacion se borro", aisladorDe("stationId"));
        casos.put("un aislador cuya via se borro", aisladorDe("trackId"));
        casos.put("un aislador cuya via conectada se borro", aisladorDe("connectedTrackId"));
        casos.put("un punto de acceso cuya via se borro", accesoDe("trackId"));
        casos.put("un punto de acceso cuya estacion se borro", accesoDe("stationId"));
        casos.put("una via cuyo paquete se borro", viaDePaqueteBorrado());
        casos.put("una estacion cuyo paquete se borro", estacionDePaqueteBorrado());

        return casos.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            catalogos();
            Caso caso = entry.getValue();
            caso.prepara().run();
            assertThatCode(() -> caso.modifica().call())
                    .as("antes de borrar su referencia, devolverla tal como se leyo no falla")
                    .doesNotThrowAnyException();

            caso.borraLaReferencia().run();

            ValidationException rechazo = catchThrowableOfType(ValidationException.class,
                    () -> caso.modifica().call());
            assertThat(rechazo).as("modificarla, tal como se leyo").isNotNull();
            assertThat(rechazo.getErrors()).extracting(Alert::getMessage).containsExactly("BUS-001");
            assertThat(rechazo.getErrors().getFirst().getFields()).containsExactly(caso.campo());

            assertThatCode(() -> caso.borra().run()).as("borrarla").doesNotThrowAnyException();
            assertThat(jdbc.queryForObject("select deleted from " + caso.tabla() + " where id = ?",
                    Boolean.class, caso.id())).isTrue();
            assertThat(ultimoEvento(caso.id(), "DELETED")).as("su evento DELETED").isNotNull();
        }));
    }

    /**
     * Solo por una relacion que el maestro no lleva tambien como coleccion (la via propia o la
     * conectada de un seccionador o de un aislador). Por una que si la lleva (la via de un perfil, la
     * estacion de un seccionador, el paquete de una via) Envers apunta el cambio en el padre antiguo,
     * que esta borrado y no se puede cargar: esa fila se borra y se da de alta donde toque.
     */
    @Test
    @DisplayName("un seccionador cuya via propia se borro se puede apuntar a una via viva")
    void apuntarlaAUnMaestroVivo() {
        long e = block();
        paquete(e);
        estacion(e - 1, e);
        via(e - 3, e);
        via(e - 4, e);
        seccionador(e - 6, e - 1, null, e - 3, null);
        trackService.delete(trackService.getById(e - 3));

        DisconnectorDTO seccionador = disconnectorService.getById(e - 6);
        seccionador.setTrackId(e - 4);
        DisconnectorDTO guardado = disconnectorService.update(seccionador);

        assertThat(guardado.getTrackId()).isEqualTo(e - 4);
        assertThat(ultimoEvento(e - 6, "UPDATED")).contains(nombre("T", e - 4));
    }

    // --- Casos -----------------------------------------------------------------------------

    private record Caso(String tabla, long id, String campo, Runnable prepara, Runnable borraLaReferencia,
                        Callable<Object> modifica, Runnable borra) {
    }

    private Caso perfilDeViaBorrada() {
        long e = block();
        return new Caso("profile", e - 2, "trackId",
                () -> { paquete(e); via(e - 1, e); perfil(e - 2, e - 1); },
                () -> trackService.delete(trackService.getById(e - 1)),
                () -> profileService.update(profileService.getById(e - 2)),
                () -> profileService.delete(profileService.getById(e - 2)));
    }

    private Caso mensulaDePerfilBorrado() {
        long e = block();
        return new Caso("cantilever", e - 3, "profileId",
                () -> { paquete(e); via(e - 1, e); perfil(e - 2, e - 1); mensula(e - 3, e - 2); },
                () -> profileService.delete(profileService.getById(e - 2)),
                () -> cantileverService.update(cantileverService.getById(e - 3)),
                () -> cantileverService.delete(cantileverService.getById(e - 3)));
    }

    private Caso brazoDeMensulaBorrada() {
        long e = block();
        return new Caso("steady_arm", e - 4, "cantileverId",
                () -> { paquete(e); via(e - 1, e); perfil(e - 2, e - 1); mensula(e - 3, e - 2); brazo(e - 4, e - 3); },
                () -> cantileverService.delete(cantileverService.getById(e - 3)),
                () -> steadyArmService.update(steadyArmService.getById(e - 4)),
                () -> steadyArmService.delete(steadyArmService.getById(e - 4)));
    }

    private Caso seccionadorDe(String campo) {
        long e = block();
        boolean enPoste = campo.equals("stationId") || campo.equals("profileId");
        return new Caso("disconnector", e - 6, campo,
                () -> {
                    paquete(e); estacion(e - 1, e); via(e - 2, e); perfil(e - 3, e - 2); via(e - 4, e); via(e - 5, e);
                    if (enPoste) {
                        seccionador(e - 6, e - 1, e - 3, null, null);
                    } else {
                        seccionador(e - 6, e - 1, null, e - 4, campo.equals("connectedTrackId") ? e - 5 : null);
                    }
                },
                () -> {
                    switch (campo) {
                        case "stationId" -> stationService.delete(stationService.getById(e - 1));
                        case "profileId" -> profileService.delete(profileService.getById(e - 3));
                        case "trackId" -> trackService.delete(trackService.getById(e - 4));
                        default -> trackService.delete(trackService.getById(e - 5));
                    }
                },
                () -> disconnectorService.update(disconnectorService.getById(e - 6)),
                () -> disconnectorService.delete(disconnectorService.getById(e - 6)));
    }

    private Caso aisladorDe(String campo) {
        long e = block();
        return new Caso("section_insulator", e - 4, campo,
                () -> { paquete(e); estacion(e - 1, e); via(e - 2, e); via(e - 3, e); aislador(e - 4, e - 1, e - 2, e - 3); },
                () -> {
                    switch (campo) {
                        case "stationId" -> stationService.delete(stationService.getById(e - 1));
                        case "trackId" -> trackService.delete(trackService.getById(e - 2));
                        default -> trackService.delete(trackService.getById(e - 3));
                    }
                },
                () -> sectionInsulatorService.update(sectionInsulatorService.getById(e - 4)),
                () -> sectionInsulatorService.delete(sectionInsulatorService.getById(e - 4)));
    }

    private Caso accesoDe(String campo) {
        long e = block();
        return new Caso("track_access_point", e - 3, campo,
                () -> { paquete(e); estacion(e - 1, e); via(e - 2, e); acceso(e - 3, e - 2, e - 1); },
                () -> {
                    if (campo.equals("trackId")) {
                        trackService.delete(trackService.getById(e - 2));
                    } else {
                        stationService.delete(stationService.getById(e - 1));
                    }
                },
                () -> trackAccessPointService.update(trackAccessPointService.getById(e - 3)),
                () -> trackAccessPointService.delete(trackAccessPointService.getById(e - 3)));
    }

    private Caso viaDePaqueteBorrado() {
        long e = block();
        return new Caso("track", e - 1, "executionPackageId",
                () -> { paquete(e); via(e - 1, e); },
                () -> executionPackageService.delete(sinHijos(e)),
                () -> { TrackDTO leida = trackService.getById(e - 1); leida.setProfiles(null); return trackService.update(leida); },
                () -> trackService.delete(trackService.getById(e - 1)));
    }

    private Caso estacionDePaqueteBorrado() {
        long e = block();
        return new Caso("station", e - 1, "executionPackageId",
                () -> { paquete(e); estacion(e - 1, e); },
                () -> executionPackageService.delete(sinHijos(e)),
                () -> stationService.update(stationService.getById(e - 1)),
                () -> stationService.delete(stationService.getById(e - 1)));
    }

    private com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO sinHijos(long id) {
        var dto = new com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO();
        dto.setId(id);
        return dto;
    }

    // --- Datos -----------------------------------------------------------------------------

    private long block() {
        next -= 20;
        return next;
    }

    private static String nombre(String kind, long id) {
        return "IT-DELREF-" + kind + id;
    }

    private static StationDTO estacionDto(String name, long packageId) {
        StationDTO dto = new StationDTO();
        dto.setName(name);
        dto.setExecutionPackageId(packageId);
        return dto;
    }

    /** Los valores del ultimo evento de esa operacion sobre ese id, como texto; null si no lo hay. */
    private String ultimoEvento(long id, String operation) {
        List<String> values = jdbc.queryForList("select (payload::json->'data'->'values')::text from outbox_message"
                + " where aggregate_id = ? and event_type like ? order by sequence_number desc limit 1",
                String.class, String.valueOf(id), "MASTER_DATA_%_" + operation);
        return values.isEmpty() ? null : values.getFirst();
    }

    private void lov(String table, long id) {
        jdbc.update("insert into " + table + " (id, code, description, enabled, create_date, create_user,"
                + " version_date, version_user, version_number)"
                + " values (?, 'IT-DELREF', 'Referencias borradas', true, now(), 'test', now(), 'test', 1)", id);
    }

    private void paquete(long id) {
        jdbc.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package, length, start_date,
                        end_date, company_id, create_date, create_user, version_date, version_user, version_number)
                values (?, ?, true, false, false, 0, date '2020-01-01', date '2020-12-31',
                        (select min(id) from business_entity), now(), 'test', now(), 'test', 1)
                """, id, nombre("EP", id));
    }

    private void estacion(long id, long packageId) {
        jdbc.update("""
                insert into station (id, name, execution_package_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, nombre("ST", id), packageId);
    }

    private void via(long id, long packageId) {
        jdbc.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, true, false, ?, now(), 'test', now(), 'test', 1)
                """, id, nombre("T", id), packageId);
    }

    private void enlace(long track, long station) {
        jdbc.update("insert into track_station (track_id, station_id) values (?, ?)", track, station);
    }

    private void perfil(long id, long track) {
        jdbc.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, ?, 1.000, ?, (select id from profile_status where code = 'PROVISIONAL'), false,
                        now(), 'test', now(), 'test', 1)
                """, id, nombre("P", id), track);
    }

    private void mensula(long id, long profile) {
        jdbc.update("""
                insert into cantilever (id, profile_id, cantilever_type_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, profile, CANTILEVER_TYPE);
    }

    private void brazo(long id, long cantilever) {
        jdbc.update("""
                insert into steady_arm (id, cantilever_id, length, steady_arm_type_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, 1000, ?, false, now(), 'test', now(), 'test', 1)
                """, id, cantilever, ARM_TYPE);
    }

    /** En un poste (con su estacion), o sin poste: con su via propia, su KP y, si la hay, la conectada. */
    private void seccionador(long id, Long station, Long profile, Long track, Long connectedTrack) {
        jdbc.update("""
                insert into disconnector (id, name, onload, station_id, profile_id, track_id, connected_track_id,
                        kilometric_point, disconnector_function_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, true, ?, ?, ?, ?, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, nombre("D", id), station, profile, track, connectedTrack,
                profile == null ? new java.math.BigDecimal("3.000") : null, FUNCTION);
    }

    private void aislador(long id, long station, long track, long connectedTrack) {
        jdbc.update("""
                insert into section_insulator (id, name, status, station_id, track_id, connected_track_id,
                        kilometric_point, installation_type, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, ?, true, ?, ?, ?, 1.500, 'TRACK_CONNECTION', false, now(), 'test', now(), 'test', 1)
                """, id, nombre("SI", id), station, track, connectedTrack);
    }

    private void acceso(long id, long track, long station) {
        jdbc.update("""
                insert into track_access_point (id, name, track_id, station_id, kilometric_point, kind, side,
                        road_rail_access, status, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, ?, ?, ?, 2.000, 'PREPARED_ACCESS', 'LEFT', true, true, false, now(), 'test', now(),
                        'test', 1)
                """, id, nombre("A", id), track, station);
    }
}
