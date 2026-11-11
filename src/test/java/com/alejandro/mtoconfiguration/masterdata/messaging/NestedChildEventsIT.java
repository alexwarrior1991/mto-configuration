package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.CantileverService;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los eventos de los hijos que se escriben dentro de la peticion de su padre, de punta a punta: los
 * servicios reales contra PostgreSQL, mirando lo que queda en {@code outbox_message}.
 *
 * <p>El servicio solo publicaba el evento de su raiz. Un perfil que se quitaba de un
 * {@code PUT /tracks/{id}}, o un seccionador de un {@code PUT /stations/{id}}, lo borraba
 * {@code orphanRemoval} en fisico y sin {@code DELETED}: {@code mto-maintenance}, que guarda perfiles,
 * seccionadores y aisladores como activos, se quedaba con el suyo habilitado para siempre, y el
 * republicado no lo arreglaba porque solo recorre lo vivo. Las altas y las modificaciones hechas
 * dentro del padre tampoco publicaban.
 *
 * <p>Lo que se comprueba aqui: que cada entidad con {@code @PublishMasterDataEvent} que se crea, se
 * modifica o se quita en anidado deja su propia fila en el outbox, en la misma transaccion; que el
 * hijo quitado se borra en logico, con su rama (la fila se queda); y que lo que no cambia <b>no</b>
 * publica, porque un {@code PUT} de un paquete con sus vias y sus miles de perfiles no puede ser
 * miles de eventos.
 *
 * <p>El punto de partida se inserta con SQL, como en {@code DisconnectorLinkIT}: un paquete con una
 * estacion (un seccionador y un aislador con una aguja) y una via con dos perfiles; el primero lleva
 * una mensula con su brazo.
 */
@SpringBootTest
@DisplayName("Los eventos de los hijos escritos dentro de su padre")
class NestedChildEventsIT {

    private static final long PACKAGE = -501L;
    private static final long STATION = -502L;
    private static final long TRACK = -503L;
    private static final long PROFILE_A = -504L;
    private static final long PROFILE_B = -505L;
    private static final long CANTILEVER = -506L;
    private static final long ARM = -507L;
    private static final long DISCONNECTOR = -508L;
    private static final long INSULATOR = -509L;
    private static final long SWITCH = -510L;
    private static final long FUNCTION = -511L;
    private static final long CANTILEVER_TYPE = -512L;
    private static final long ARM_TYPE = -513L;
    private static final long SECTIONING = -514L;
    private static final String LOV_CODE = "IT-NEST";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TrackService trackService;
    @Autowired
    private StationService stationService;
    @Autowired
    private ExecutionPackageService executionPackageService;
    @Autowired
    private CantileverService cantileverService;

    /** El ultimo evento que habia antes de la escritura que se prueba. */
    private long marca;

    @BeforeEach
    void inserta() {
        limpia();
        for (String table : List.of("disconnector_function", "cantilever_type", "steady_arm_type", "sectioning")) {
            jdbcTemplate.update("insert into " + table + " (id, code, description, enabled, create_date,"
                    + " create_user, version_date, version_user, version_number)"
                    + " values (?, ?, 'Eventos anidados', true, now(), 'test', now(), 'test', 1)",
                    lovId(table), LOV_CODE);
        }
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, company_id, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-NEST', true, false, false, 0, date '2020-01-01', date '2020-12-31',
                        (select min(id) from business_entity), now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-NEST-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-NEST-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("insert into track_station (track_id, station_id) values (?, ?)", TRACK, STATION);
        perfil(PROFILE_A, "IT-NEST-A", "1.000");
        perfil(PROFILE_B, "IT-NEST-B", "2.000");
        // Un conjunto de referencias en el perfil y otro en la via (su estacion): devolverlos
        // tal cual no puede reescribirlos, o el perfil y la via saldrian como modificados.
        jdbcTemplate.update("insert into profile_sectioning (profile_id, sectioning_id) values (?, ?)",
                PROFILE_B, SECTIONING);
        jdbcTemplate.update("""
                insert into cantilever (id, cw_height, stagger, profile_id, cantilever_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 5.500, 200, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, CANTILEVER, PROFILE_A, CANTILEVER_TYPE);
        jdbcTemplate.update("""
                insert into steady_arm (id, length, cantilever_id, steady_arm_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 1000, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, ARM, CANTILEVER, ARM_TYPE);
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, station_id, disconnector_function_id,
                        kilometric_point, track_id, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, 'IT-NEST-DISC', true, ?, ?, 1.500, ?, false, now(), 'test', now(), 'test', 1)
                """, DISCONNECTOR, STATION, FUNCTION, TRACK);
        jdbcTemplate.update("""
                insert into section_insulator (id, name, status, station_id, kilometric_point,
                        installation_type, track_id, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, 'IT-NEST-SI', true, ?, 1.700, 'IN_TRACK', ?, false, now(), 'test', now(), 'test', 1)
                """, INSULATOR, STATION, TRACK);
        jdbcTemplate.update("""
                insert into section_insulator_switch (id, code, status, section_insulator_id,
                        kilometric_point, turnout_denominator, track_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'W1', true, ?, 1.700, 9, ?, false, now(), 'test', now(), 'test', 1)
                """, SWITCH, INSULATOR, TRACK);
        marca = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence_number), 0) from outbox_message", Long.class);
    }

    private static long lovId(String table) {
        return switch (table) {
            case "disconnector_function" -> FUNCTION;
            case "cantilever_type" -> CANTILEVER_TYPE;
            case "sectioning" -> SECTIONING;
            default -> ARM_TYPE;
        };
    }

    private void perfil(long id, String code, String kp) {
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, ?, ?::numeric, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, id, code, kp, TRACK);
    }

    @AfterEach
    void limpia() {
        String tracks = "(select id from track where execution_package_id = " + PACKAGE + ")";
        String stations = "(select id from station where execution_package_id = " + PACKAGE + ")";
        String profiles = "(select id from profile where track_id in " + tracks + ")";
        String cantilevers = "(select id from cantilever where profile_id in " + profiles + ")";
        String insulators = "(select id from section_insulator where station_id in " + stations + ")";
        jdbcTemplate.update("delete from outbox_message where payload like '%IT-NEST%'");
        // Los eventos de la mensula y del brazo no llevan ningun nombre: se borran por su id.
        jdbcTemplate.update("delete from outbox_message where aggregate_type = 'steady-arm' and aggregate_id in"
                + " (select id::text from steady_arm where cantilever_id in " + cantilevers + ")");
        jdbcTemplate.update("delete from outbox_message where aggregate_type = 'cantilever' and aggregate_id in"
                + " (select id::text from cantilever where profile_id in " + profiles + ")");
        for (String suffix : List.of("_aud", "")) {
            jdbcTemplate.update("delete from steady_arm" + suffix + " where cantilever_id in " + cantilevers);
        }
        jdbcTemplate.update("delete from cantilever_aud where profile_id in " + profiles);
        jdbcTemplate.update("delete from cantilever where profile_id in " + profiles);
        jdbcTemplate.update("delete from section_insulator_switch_aud where section_insulator_id in " + insulators);
        jdbcTemplate.update("delete from section_insulator_switch where section_insulator_id in " + insulators);
        jdbcTemplate.update("delete from section_insulator_aud where station_id in " + stations);
        jdbcTemplate.update("delete from section_insulator where station_id in " + stations);
        jdbcTemplate.update("delete from disconnector_aud where station_id in " + stations);
        jdbcTemplate.update("delete from disconnector where station_id in " + stations);
        jdbcTemplate.update("delete from profile_sectioning_aud where profile_id in " + profiles);
        jdbcTemplate.update("delete from profile_sectioning where profile_id in " + profiles);
        jdbcTemplate.update("delete from profile_aud where track_id in " + tracks);
        jdbcTemplate.update("delete from profile where track_id in " + tracks);
        jdbcTemplate.update("delete from track_station_aud where track_id in " + tracks);
        jdbcTemplate.update("delete from track_station where track_id in " + tracks);
        jdbcTemplate.update("delete from track_aud where execution_package_id = ?", PACKAGE);
        jdbcTemplate.update("delete from track where execution_package_id = ?", PACKAGE);
        jdbcTemplate.update("delete from station_aud where execution_package_id = ?", PACKAGE);
        jdbcTemplate.update("delete from station where execution_package_id = ?", PACKAGE);
        jdbcTemplate.update("delete from execution_package_aud where id = ?", PACKAGE);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
        for (String table : List.of("disconnector_function", "cantilever_type", "steady_arm_type", "sectioning")) {
            jdbcTemplate.update("delete from " + table + "_aud where id = ?", lovId(table));
            jdbcTemplate.update("delete from " + table + " where id = ?", lovId(table));
        }
    }

    /** Lo que la escritura ha dejado en el outbox, en su orden: {@code profile.deleted#-504}. */
    private List<String> eventos() {
        return jdbcTemplate.queryForList("""
                select replace(routing_key, 'mto.master-data.', '') || '#' || aggregate_id
                  from outbox_message where sequence_number > ? order by sequence_number
                """, String.class, marca);
    }

    private JsonNode values(String entity, long id) {
        String payload = jdbcTemplate.queryForObject("""
                select payload from outbox_message
                 where sequence_number > ? and aggregate_type = ? and aggregate_id = ?
                """, String.class, marca, entity, String.valueOf(id));
        return objectMapper.readTree(payload).at("/data/values");
    }

    private Map<String, Object> fila(String table, long id) {
        return jdbcTemplate.queryForMap("select deleted, version_number from " + table + " where id = ?", id);
    }

    @Test
    @DisplayName("quitar un perfil en el PUT de su via publica su DELETED y el de su mensula y su brazo, y las filas se quedan")
    void quitarUnPerfilEnElPutDeLaVia() {
        TrackDTO via = trackService.getById(TRACK);
        via.getProfiles().removeIf(perfil -> perfil.getId().equals(PROFILE_A));

        TrackDTO guardada = trackService.update(via);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "track.updated#" + TRACK,
                "profile.deleted#" + PROFILE_A,
                "cantilever.deleted#" + CANTILEVER,
                "steady-arm.deleted#" + ARM);
        assertThat(eventos()).as("primero la raiz, como siempre").first().isEqualTo("track.updated#" + TRACK);
        // Borrado logico, como el DELETE directo: la fila sigue, marcada, con su historial.
        assertThat(fila("profile", PROFILE_A)).containsEntry("deleted", true);
        assertThat(fila("cantilever", CANTILEVER)).containsEntry("deleted", true);
        assertThat(fila("steady_arm", ARM)).containsEntry("deleted", true);
        assertThat(fila("profile", PROFILE_B)).containsEntry("deleted", false).containsEntry("version_number", 1);
        // El DELETED lleva los values de siempre, y la via ya no trae al perfil.
        assertThat(values("profile", PROFILE_A).get("profileId").asText()).isEqualTo("IT-NEST-A");
        assertThat(values("profile", PROFILE_A).at("/track/id").asLong()).isEqualTo(TRACK);
        assertThat(values("track", TRACK).get("profiles")).hasSize(1);
        assertThat(guardada.getProfiles()).extracting(ProfileDTO::getId).containsExactly(PROFILE_B);
        assertThat(trackService.getById(TRACK).getProfiles()).extracting(ProfileDTO::getId).containsExactly(PROFILE_B);
    }

    @Test
    @DisplayName("quitar el seccionador en el PUT de su estacion publica su DELETED; el aislador, que no cambia, no publica")
    void quitarUnSeccionadorEnElPutDeLaEstacion() {
        StationDTO estacion = stationService.getById(STATION);
        estacion.getDisconnectors().clear();

        StationDTO guardada = stationService.update(estacion);

        assertThat(eventos())
                .contains("station.updated#" + STATION, "disconnector.deleted#" + DISCONNECTOR)
                .noneMatch(evento -> evento.startsWith("section-insulator."))
                .noneMatch(evento -> evento.startsWith("profile."));
        assertThat(fila("disconnector", DISCONNECTOR)).containsEntry("deleted", true);
        assertThat(fila("section_insulator", INSULATOR)).containsEntry("deleted", false);
        assertThat(values("disconnector", DISCONNECTOR).get("name").asText()).isEqualTo("IT-NEST-DISC");
        assertThat(guardada.getDisconnectors()).isEmpty();
    }

    @Test
    @DisplayName("quitar el aislador en el PUT de su estacion publica su DELETED y borra en logico sus agujas")
    void quitarUnAisladorEnElPutDeLaEstacion() {
        StationDTO estacion = stationService.getById(STATION);
        estacion.getSectionInsulators().clear();

        stationService.update(estacion);

        assertThat(eventos())
                .contains("section-insulator.deleted#" + INSULATOR)
                .noneMatch(evento -> evento.startsWith("disconnector."));
        assertThat(fila("section_insulator", INSULATOR)).containsEntry("deleted", true);
        assertThat(fila("section_insulator_switch", SWITCH)).containsEntry("deleted", true);
    }

    @Test
    @DisplayName("un perfil modificado y otro dado de alta dentro del PUT de la via publican UPDATED y CREATED; el que no cambia, nada")
    void altaYModificacionAnidadas() {
        TrackDTO via = trackService.getById(TRACK);
        via.getProfiles().stream().filter(perfil -> perfil.getId().equals(PROFILE_B)).findFirst().orElseThrow()
                .setSpan(new BigDecimal("47.970"));
        ProfileDTO nuevo = new ProfileDTO();
        nuevo.setProfileId("IT-NEST-C");
        nuevo.setKp("3.000");
        ProfileStatusDTO status = new ProfileStatusDTO();
        status.setCode("PROVISIONAL");
        nuevo.setProfileStatus(status);
        via.getProfiles().add(nuevo);

        TrackDTO guardada = trackService.update(via);

        Long creado = guardada.getProfiles().stream()
                .filter(perfil -> "IT-NEST-C".equals(perfil.getProfileId())).findFirst().orElseThrow().getId();
        assertThat(creado).isNotNull();
        assertThat(eventos()).containsExactlyInAnyOrder(
                "track.updated#" + TRACK,
                "profile.updated#" + PROFILE_B,
                "profile.created#" + creado);
        assertThat(values("profile", PROFILE_B).get("span").decimalValue()).isEqualByComparingTo("47.970");
        assertThat(values("profile", creado).get("profileId").asText()).isEqualTo("IT-NEST-C");
        assertThat(values("profile", creado).at("/track/id").asLong()).isEqualTo(TRACK);
        assertThat(fila("profile", PROFILE_A)).containsEntry("version_number", 1);
    }

    @Test
    @DisplayName("devolver la via tal como se leyo solo publica el evento de la via")
    void sinCambiosSoloPublicaLaRaiz() {
        trackService.update(trackService.getById(TRACK));

        assertThat(eventos()).containsExactly("track.updated#" + TRACK);
        assertThat(fila("profile", PROFILE_A)).containsEntry("version_number", 1);
        assertThat(fila("cantilever", CANTILEVER)).containsEntry("version_number", 1);
        assertThat(fila("steady_arm", ARM)).containsEntry("version_number", 1);
    }

    @Test
    @DisplayName("devolver el paquete entero tal como se leyo solo publica el evento del paquete")
    void elPaqueteSinCambiosSoloPublicaLaRaiz() {
        executionPackageService.update(executionPackageService.getById(PACKAGE));

        assertThat(eventos()).containsExactly("execution-package.updated#" + PACKAGE);
        assertThat(fila("track", TRACK)).containsEntry("version_number", 1);
        assertThat(fila("station", STATION)).containsEntry("version_number", 1);
    }

    @Test
    @DisplayName("quitar la via en el PUT de su paquete publica el DELETED de la via y el de toda su rama")
    void quitarUnaViaEnElPutDelPaquete() {
        ExecutionPackageDTO paquete = executionPackageService.getById(PACKAGE);
        paquete.getTracks().clear();

        executionPackageService.update(paquete);

        assertThat(eventos()).contains(
                "execution-package.updated#" + PACKAGE,
                "track.deleted#" + TRACK,
                "profile.deleted#" + PROFILE_A,
                "profile.deleted#" + PROFILE_B,
                "cantilever.deleted#" + CANTILEVER,
                "steady-arm.deleted#" + ARM);
        assertThat(eventos()).noneMatch(evento -> evento.startsWith("disconnector."));
        assertThat(fila("track", TRACK)).containsEntry("deleted", true);
        assertThat(fila("profile", PROFILE_B)).containsEntry("deleted", true);
        // Lo que no cuelga de la via se queda, como con DELETE /tracks/{id}.
        assertThat(fila("disconnector", DISCONNECTOR)).containsEntry("deleted", false);
        assertThat(fila("station", STATION)).containsEntry("deleted", false);
    }

    @Test
    @DisplayName("una via dada de alta con un perfil dentro publica el CREATED de los dos")
    void altaDeUnaViaConUnPerfilDentro() {
        TrackDTO via = new TrackDTO();
        via.setName("IT-NEST-TRACK-2");
        via.setEnabled(true);
        via.setExecutionPackageId(PACKAGE);
        ProfileDTO perfil = new ProfileDTO();
        perfil.setProfileId("IT-NEST-D");
        perfil.setKp("9.000");
        ProfileStatusDTO status = new ProfileStatusDTO();
        status.setCode("PROVISIONAL");
        perfil.setProfileStatus(status);
        via.getProfiles().add(perfil);

        TrackDTO creada = trackService.create(via);

        Long perfilId = creada.getProfiles().getFirst().getId();
        assertThat(eventos()).containsExactly(
                "track.created#" + creada.getId(),
                "profile.created#" + perfilId);
    }

    @Test
    @DisplayName("quitar el brazo en el PUT de su mensula publica su DELETED, y otro brazo entra despues con su CREATED")
    void quitarYReponerElBrazoEnElPutDeLaMensula() {
        CantileverDTO mensula = cantileverService.getById(CANTILEVER);
        mensula.setSteadyArm(null);

        CantileverDTO sinBrazo = cantileverService.update(mensula);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "cantilever.updated#" + CANTILEVER, "steady-arm.deleted#" + ARM);
        assertThat(fila("steady_arm", ARM)).containsEntry("deleted", true);
        assertThat(values("cantilever", CANTILEVER).get("steadyArm").isNull()).isTrue();

        marca = jdbcTemplate.queryForObject("select max(sequence_number) from outbox_message", Long.class);
        SteadyArmTypeDTO tipo = new SteadyArmTypeDTO();
        tipo.setCode(LOV_CODE);
        SteadyArmDTO otro = new SteadyArmDTO();
        otro.setLength(1300L);
        otro.setSteadyArmType(tipo);
        sinBrazo.setSteadyArm(otro);

        CantileverDTO conBrazo = cantileverService.update(sinBrazo);

        Long nuevo = conBrazo.getSteadyArm().getId();
        assertThat(nuevo).isNotNull().isNotEqualTo(ARM);
        assertThat(eventos()).containsExactlyInAnyOrder(
                "cantilever.updated#" + CANTILEVER, "steady-arm.created#" + nuevo);
        assertThat(values("cantilever", CANTILEVER).at("/steadyArm/id").asLong()).isEqualTo(nuevo);
    }

    @Test
    @DisplayName("si la peticion se rechaza, no queda ni el borrado ni ningun evento")
    void unRechazoNoDejaNada() {
        TrackDTO via = trackService.getById(TRACK);
        via.getProfiles().removeIf(perfil -> perfil.getId().equals(PROFILE_A));
        via.getProfiles().getFirst().setVersionNumber(99);

        assertThatThrownBy(() -> trackService.update(via)).isInstanceOf(ConcurrencyException.class);

        assertThat(eventos()).isEmpty();
        assertThat(fila("profile", PROFILE_A)).containsEntry("deleted", false);
        assertThat(fila("cantilever", CANTILEVER)).containsEntry("deleted", false);
    }
}
