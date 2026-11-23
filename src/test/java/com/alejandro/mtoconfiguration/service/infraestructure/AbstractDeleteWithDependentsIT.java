package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.EntityInUseException;
import com.alejandro.mtoconfiguration.core.exception.EntityInUseException.Dependent;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.support.SqlStatementRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Borrar un maestro del que cuelgan cosas vivas, y borrar una via entera, de punta a punta: los
 * servicios reales contra PostgreSQL, mirando las filas y lo que queda en {@code outbox_message}.
 *
 * <p>El borrado es logico y ninguna clave ajena lo frenaba: el {@code DELETE} de una estacion dejaba
 * sus seccionadores apuntando a una estacion que ya nadie podia leer. Ahora es un 409 que dice que
 * cuelga y cuanto, y no escribe nada. La via tiene ademas una forma de irse entera, con todo lo
 * suyo y sin tocar nada de otra via.
 *
 * <p>El punto de partida, insertado con SQL: un paquete con una estacion y dos vias. La via T
 * (enlazada a la estacion) tiene dos perfiles: en el poste del A hay un seccionador que maniobra la
 * otra via, y el B lleva una mensula con su brazo; ademas, un seccionador sin poste, un aislador
 * con una aguja y un punto de acceso. La via U solo tiene un perfil.
 *
 * <p>Los casos viven aqui y corren dos veces: {@link DeleteWithDependentsIT}, con las tablas en
 * {@code public} como el resto de los IT, y {@link DeleteWithDependentsInOwnSchemaIT}, con ellas
 * en otro esquema, como en la plataforma. Cada una pone sus propiedades.
 */
abstract class AbstractDeleteWithDependentsIT {

    private static final long PACKAGE = -701L;
    private static final long STATION = -702L;
    private static final long TRACK = -703L;
    private static final long OTHER_TRACK = -704L;
    private static final long PROFILE_A = -705L;
    private static final long PROFILE_B = -706L;
    private static final long OTHER_PROFILE = -707L;
    private static final long CANTILEVER = -708L;
    private static final long ARM = -709L;
    private static final long DISCONNECTOR_ON_POLE = -710L;
    private static final long DISCONNECTOR_NO_POLE = -711L;
    private static final long INSULATOR = -712L;
    private static final long SWITCH = -713L;
    private static final long ACCESS_POINT = -714L;
    private static final long FUNCTION = -715L;
    private static final long CANTILEVER_TYPE = -716L;
    private static final long ARM_TYPE = -717L;
    private static final long FOREIGN_DISCONNECTOR = -718L;
    private static final long FOREIGN_INSULATOR = -719L;
    private static final long FOREIGN_SWITCH = -720L;
    private static final String LOV_CODE = "IT-DEL";

    /**
     * Los perfiles de mas de la via en «no lee fila a fila», cada uno con su mensula y su brazo. Con
     * {@code -Dmto.test.cascade.profiles=3000} el mismo caso sirve para medir una via de verdad.
     */
    private static final int MANY_PROFILES = Integer.getInteger("mto.test.cascade.profiles", 60);

    @Autowired
    private JdbcTemplate contextJdbcTemplate;
    /** Con lo que se insertan y se miran las filas: ver {@link #fixtures(JdbcTemplate)}. */
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ExecutionPackageService executionPackageService;
    @Autowired
    private StationService stationService;
    @Autowired
    private TrackService trackService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private CantileverService cantileverService;
    @Autowired
    private SectionInsulatorService sectionInsulatorService;

    /** El ultimo evento que habia antes de la escritura que se prueba. */
    private long marca;

    /**
     * El {@code JdbcTemplate} del SQL a mano de esta clase, que nombra las tablas sin esquema. Aqui
     * es el del contexto; {@code DeleteWithDependentsInOwnSchemaIT} da otro, cuya conexion si ve el
     * esquema en el que el pone las tablas.
     */
    protected JdbcTemplate fixtures(JdbcTemplate ofTheContext) {
        return ofTheContext;
    }

    @BeforeEach
    void inserta() {
        jdbcTemplate = fixtures(contextJdbcTemplate);
        limpia();
        for (String table : List.of("disconnector_function", "cantilever_type", "steady_arm_type")) {
            jdbcTemplate.update("insert into " + table + " (id, code, description, enabled, create_date,"
                    + " create_user, version_date, version_user, version_number)"
                    + " values (?, ?, 'Borrado con dependientes', true, now(), 'test', now(), 'test', 1)",
                    lovId(table), LOV_CODE);
        }
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, company_id, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-DEL', true, false, false, 0, date '2020-01-01', date '2020-12-31',
                        (select min(id) from business_entity), now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-DEL-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        via(TRACK, "IT-DEL-TRACK");
        via(OTHER_TRACK, "IT-DEL-OTHER");
        jdbcTemplate.update("insert into track_station (track_id, station_id) values (?, ?)", TRACK, STATION);
        perfil(PROFILE_A, "IT-DEL-A", "1.000", TRACK);
        perfil(PROFILE_B, "IT-DEL-B", "2.000", TRACK);
        perfil(OTHER_PROFILE, "IT-DEL-U", "1.000", OTHER_TRACK);
        jdbcTemplate.update("""
                insert into cantilever (id, cw_height, stagger, profile_id, cantilever_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 5.500, 200, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, CANTILEVER, PROFILE_B, CANTILEVER_TYPE);
        jdbcTemplate.update("""
                insert into steady_arm (id, length, cantilever_id, steady_arm_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                values (?, 1000, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, ARM, CANTILEVER, ARM_TYPE);
        // En un poste de la via, maniobrando la otra: esta en T, y se va con T.
        seccionador(DISCONNECTOR_ON_POLE, "IT-DEL-DISC-POLE", STATION, PROFILE_A, null, OTHER_TRACK, null);
        seccionador(DISCONNECTOR_NO_POLE, "IT-DEL-DISC-FREE", null, null, "1.500", TRACK, null);
        aislador(INSULATOR, "IT-DEL-SI", STATION, TRACK, null);
        aguja(SWITCH, INSULATOR, TRACK);
        jdbcTemplate.update("""
                insert into track_access_point (id, name, track_id, station_id, kilometric_point, kind, side,
                        road_rail_access, status, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, 'IT-DEL-ACCESS', ?, ?, 2.000, 'PREPARED_ACCESS', 'LEFT', true, true, false,
                        now(), 'test', now(), 'test', 1)
                """, ACCESS_POINT, TRACK, STATION);
        marca = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence_number), 0) from outbox_message", Long.class);
    }

    private static long lovId(String table) {
        return switch (table) {
            case "disconnector_function" -> FUNCTION;
            case "cantilever_type" -> CANTILEVER_TYPE;
            default -> ARM_TYPE;
        };
    }

    private void via(long id, String name) {
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, ?, true, false, ?, now(), 'test', now(), 'test', 1)
                """, id, name, PACKAGE);
    }

    private void perfil(long id, String code, String kp, long track) {
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, ?, ?::numeric, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, id, code, kp, track);
    }

    private void seccionador(long id, String name, Long station, Long profile, String kp, Long track,
                             Long connectedTrack) {
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, station_id, profile_id, disconnector_function_id,
                        kilometric_point, track_id, connected_track_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, true, ?, ?, ?, ?::numeric, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, name, station, profile, FUNCTION, kp, track, connectedTrack);
    }

    private void aislador(long id, String name, Long station, Long track, Long connectedTrack) {
        jdbcTemplate.update("""
                insert into section_insulator (id, name, status, station_id, kilometric_point,
                        installation_type, track_id, connected_track_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, true, ?, 1.700, ?, ?, ?, false, now(), 'test', now(), 'test', 1)
                """, id, name, station, connectedTrack == null ? "IN_TRACK" : "TRACK_CONNECTION", track,
                connectedTrack);
    }

    private void aguja(long id, long insulator, long track) {
        jdbcTemplate.update("""
                insert into section_insulator_switch (id, code, status, section_insulator_id,
                        kilometric_point, turnout_denominator, track_id, deleted, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'W1', true, ?, 1.700, 9, ?, false, now(), 'test', now(), 'test', 1)
                """, id, insulator, track);
    }

    @AfterEach
    void limpia() {
        if (jdbcTemplate == null) {
            return;
        }
        String tracks = "(select id from track where execution_package_id = " + PACKAGE + ")";
        String profiles = "(select id from profile where track_id in " + tracks + ")";
        String cantilevers = "(select id from cantilever where profile_id in " + profiles + ")";
        String insulators = "(select id from section_insulator where track_id in " + tracks + ")";
        String disconnectors = "(select id from disconnector where track_id in " + tracks
                + " or connected_track_id in " + tracks + " or profile_id in " + profiles + ")";
        jdbcTemplate.update("delete from outbox_message where payload like '%IT-DEL%'");
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
        jdbcTemplate.update("delete from section_insulator_aud where track_id in " + tracks);
        jdbcTemplate.update("delete from section_insulator where track_id in " + tracks);
        jdbcTemplate.update("delete from disconnector_aud where id in " + disconnectors);
        jdbcTemplate.update("delete from disconnector where id in " + disconnectors);
        jdbcTemplate.update("delete from track_access_point_aud where track_id in " + tracks);
        jdbcTemplate.update("delete from track_access_point where track_id in " + tracks);
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
        for (String table : List.of("disconnector_function", "cantilever_type", "steady_arm_type")) {
            jdbcTemplate.update("delete from " + table + "_aud where id = ?", lovId(table));
            jdbcTemplate.update("delete from " + table + " where id = ?", lovId(table));
        }
    }

    /** Lo que la escritura ha dejado en el outbox, en su orden: {@code profile.deleted#-705}. */
    private List<String> eventos() {
        return jdbcTemplate.queryForList("""
                select replace(routing_key, 'mto.master-data.', '') || '#' || aggregate_id
                  from outbox_message where sequence_number > ? order by sequence_number
                """, String.class, marca);
    }

    private boolean borrada(String table, long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select deleted from " + table + " where id = ?", Boolean.class, id));
    }

    /** Cuantas filas vivas quedan de todo lo que se inserto: si un rechazo escribe algo, baja. */
    private int vivas() {
        return jdbcTemplate.queryForObject("""
                select (select count(*) from execution_package where id = %1$d and deleted = false)
                     + (select count(*) from station where execution_package_id = %1$d and deleted = false)
                     + (select count(*) from track where execution_package_id = %1$d and deleted = false)
                     + (select count(*) from profile where id in (%2$d, %3$d, %4$d) and deleted = false)
                     + (select count(*) from cantilever where id = %5$d and deleted = false)
                     + (select count(*) from steady_arm where id = %6$d and deleted = false)
                     + (select count(*) from disconnector where id in (%7$d, %8$d, %9$d) and deleted = false)
                     + (select count(*) from section_insulator where id in (%10$d, %11$d) and deleted = false)
                     + (select count(*) from section_insulator_switch where id in (%12$d, %13$d) and deleted = false)
                     + (select count(*) from track_access_point where id = %14$d and deleted = false)
                """.formatted(PACKAGE, PROFILE_A, PROFILE_B, OTHER_PROFILE, CANTILEVER, ARM, DISCONNECTOR_ON_POLE,
                DISCONNECTOR_NO_POLE, FOREIGN_DISCONNECTOR, INSULATOR, FOREIGN_INSULATOR, SWITCH, FOREIGN_SWITCH,
                ACCESS_POINT), Integer.class);
    }

    private static <D extends BaseDTO> D conId(D dto, long id) {
        dto.setId(id);
        return dto;
    }

    private EntityInUseException rechazo(Runnable borrado) {
        int antes = vivas();
        Throwable thrown = catchThrowable(borrado::run);

        assertThat(thrown).isInstanceOf(EntityInUseException.class);
        // Sin escribir nada: ni una fila borrada, ni un evento.
        assertThat(vivas()).isEqualTo(antes);
        assertThat(eventos()).isEmpty();

        return (EntityInUseException) thrown;
    }

    // --- El DELETE de un maestro con algo vivo colgando es un 409 que dice que cuelga, y no escribe nada ---

    @Test
    @DisplayName("un paquete con vias y estaciones")
    void paquete() {
        EntityInUseException rechazo = rechazo(
                () -> executionPackageService.delete(conId(new ExecutionPackageDTO(), PACKAGE)));

        assertThat(rechazo.getDetail()).contains("el paquete de ejecución", "2 vías y 1 estación");
        assertThat(rechazo.getDependents()).containsExactly(
                new Dependent("tracks", 2), new Dependent("stations", 1));
    }

    @Test
    @DisplayName("una estacion con una via enlazada, un seccionador, un aislador y un punto de acceso")
    void estacion() {
        EntityInUseException rechazo = rechazo(() -> stationService.delete(conId(new StationDTO(), STATION)));

        assertThat(rechazo.getDetail())
                .contains("la estación", "1 vía enlazada, 1 seccionador, 1 aislador y 1 punto de acceso");
    }

    @Test
    @DisplayName("una via con perfiles, seccionadores, un aislador y un punto de acceso, y dice como borrarla entera")
    void via() {
        EntityInUseException rechazo = rechazo(() -> trackService.delete(conId(new TrackDTO(), TRACK)));

        assertThat(rechazo.getDetail())
                .contains("la vía", "2 perfiles, 2 seccionadores, 1 aislador y 1 punto de acceso",
                        "borra la vía entera con todo lo suyo.")
                .doesNotContain("DELETE", "cascade");
        assertThat(rechazo.getDependents()).containsExactly(
                new Dependent("profiles", 2), new Dependent("disconnectors", 2),
                new Dependent("sectionInsulators", 1), new Dependent("trackAccessPoints", 1));
    }

    @Test
    @DisplayName("una via sin nada suyo, pero que otra maniobra o conecta: tambien cuelga de ella")
    void viaNombradaDesdeOtra() {
        jdbcTemplate.update("update profile set deleted = true where id = ?", OTHER_PROFILE);
        aislador(FOREIGN_INSULATOR, "IT-DEL-SI-CONN", null, TRACK, OTHER_TRACK);

        EntityInUseException rechazo = rechazo(() -> trackService.delete(conId(new TrackDTO(), OTHER_TRACK)));

        // El seccionador del poste A la maniobra, y el aislador la tiene de via conectada.
        assertThat(rechazo.getDetail()).contains("1 seccionador y 1 aislador");
    }

    @Test
    @DisplayName("un perfil con un seccionador en su poste")
    void perfil() {
        EntityInUseException rechazo = rechazo(() -> profileService.delete(conId(new ProfileDTO(), PROFILE_A)));

        assertThat(rechazo.getDetail()).contains("el perfil", "1 seccionador");
    }

    @Test
    @DisplayName("lo que cuelga y ya esta borrado no impide nada")
    void loBorradoNoCuenta() {
        jdbcTemplate.update("update profile set deleted = true where id = ?", OTHER_PROFILE);
        jdbcTemplate.update("update disconnector set deleted = true where id = ?", DISCONNECTOR_ON_POLE);

        trackService.delete(conId(new TrackDTO(), OTHER_TRACK));

        assertThat(borrada("track", OTHER_TRACK)).isTrue();
        assertThat(eventos()).containsExactly("track.deleted#" + OTHER_TRACK);
    }

    // --- Un alta comprueba que su padre sigue vivo: tambien con las tablas en otro esquema ---

    @Test
    @DisplayName("una estacion y una via se dan de alta en su paquete")
    void altaDeEstacionYDeVia() {
        StationDTO estacion = new StationDTO();
        estacion.setName("IT-DEL-STATION-NEW");
        estacion.setExecutionPackageId(PACKAGE);
        TrackDTO via = new TrackDTO();
        via.setName("IT-DEL-TRACK-NEW");
        via.setEnabled(true);
        via.setExecutionPackageId(PACKAGE);

        Long estacionId = stationService.create(estacion).getId();
        Long viaId = trackService.create(via).getId();

        assertThat(borrada("station", estacionId)).isFalse();
        assertThat(borrada("track", viaId)).isFalse();
        assertThat(eventos()).containsExactly("station.created#" + estacionId, "track.created#" + viaId);
    }

    // --- Lo que no existe sin su padre se va con el, y publica su DELETED ---

    @Test
    @DisplayName("un perfil sin seccionador se borra con su mensula y el brazo de ella")
    void perfilConSuMensula() {
        profileService.delete(conId(new ProfileDTO(), PROFILE_B));

        assertThat(borrada("profile", PROFILE_B)).isTrue();
        assertThat(borrada("cantilever", CANTILEVER)).isTrue();
        assertThat(borrada("steady_arm", ARM)).isTrue();
        // El del perfil, que es lo que se pidio borrar, delante; los de su rama, como los escribe el flush.
        assertThat(eventos().getFirst()).isEqualTo("profile.deleted#" + PROFILE_B);
        assertThat(eventos()).containsExactlyInAnyOrder(
                "profile.deleted#" + PROFILE_B,
                "cantilever.deleted#" + CANTILEVER,
                "steady-arm.deleted#" + ARM);
        assertThat(borrada("profile", PROFILE_A)).isFalse();
    }

    @Test
    @DisplayName("una mensula se borra con su brazo")
    void mensulaConSuBrazo() {
        cantileverService.delete(conId(new CantileverDTO(), CANTILEVER));

        assertThat(borrada("steady_arm", ARM)).isTrue();
        assertThat(eventos()).containsExactly("cantilever.deleted#" + CANTILEVER, "steady-arm.deleted#" + ARM);
    }

    @Test
    @DisplayName("un aislador se borra con sus agujas")
    void aisladorConSusAgujas() {
        sectionInsulatorService.delete(conId(new SectionInsulatorDTO(), INSULATOR));

        assertThat(borrada("section_insulator", INSULATOR)).isTrue();
        assertThat(borrada("section_insulator_switch", SWITCH)).isTrue();
        assertThat(eventos()).containsExactly("section-insulator.deleted#" + INSULATOR);
    }

    // --- Borrar una via entera (cascade=true) ---

    @Test
    @DisplayName("borra la via y todo lo suyo, publica el DELETED de cada cosa con el de la via delante, y no toca nada de otra via")
    void todoLoSuyo() {
        int antes = vivas();

        trackService.deleteWithEverything(TRACK);

        assertThat(borrada("track", TRACK)).isTrue();
        assertThat(borrada("profile", PROFILE_A)).isTrue();
        assertThat(borrada("profile", PROFILE_B)).isTrue();
        assertThat(borrada("cantilever", CANTILEVER)).isTrue();
        assertThat(borrada("steady_arm", ARM)).isTrue();
        assertThat(borrada("disconnector", DISCONNECTOR_ON_POLE)).isTrue();
        assertThat(borrada("disconnector", DISCONNECTOR_NO_POLE)).isTrue();
        assertThat(borrada("section_insulator", INSULATOR)).isTrue();
        assertThat(borrada("section_insulator_switch", SWITCH)).isTrue();
        assertThat(borrada("track_access_point", ACCESS_POINT)).isTrue();
        // Diez filas, y ninguna mas: el paquete, la estacion, la otra via y su perfil siguen.
        assertThat(vivas()).isEqualTo(antes - 10);
        assertThat(borrada("track", OTHER_TRACK)).isFalse();
        assertThat(borrada("profile", OTHER_PROFILE)).isFalse();
        assertThat(borrada("station", STATION)).isFalse();
        assertThat(borrada("execution_package", PACKAGE)).isFalse();

        List<String> eventos = eventos();
        assertThat(eventos.getFirst()).isEqualTo("track.deleted#" + TRACK);
        assertThat(eventos).containsExactlyInAnyOrder(
                "track.deleted#" + TRACK,
                "profile.deleted#" + PROFILE_A,
                "profile.deleted#" + PROFILE_B,
                "cantilever.deleted#" + CANTILEVER,
                "steady-arm.deleted#" + ARM,
                "disconnector.deleted#" + DISCONNECTOR_ON_POLE,
                "disconnector.deleted#" + DISCONNECTOR_NO_POLE,
                "section-insulator.deleted#" + INSULATOR,
                "track-access-point.deleted#" + ACCESS_POINT);
    }

    @Test
    @DisplayName("no lee fila a fila: las lecturas de una via con muchos perfiles son las de una con dos")
    void noLeeFilaAFila() {
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                select -100000 - n, 'IT-DEL-M' || n, 10 + n, ?,
                       (select id from profile_status where code = 'PROVISIONAL'),
                       false, now(), 'test', now(), 'test', 1
                  from generate_series(1, ?) n
                """, TRACK, MANY_PROFILES);
        jdbcTemplate.update("""
                insert into cantilever (id, cw_height, stagger, profile_id, cantilever_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                select -200000 - n, 5.500, 200, -100000 - n, ?, false, now(), 'test', now(), 'test', 1
                  from generate_series(1, ?) n
                """, CANTILEVER_TYPE, MANY_PROFILES);
        jdbcTemplate.update("""
                insert into steady_arm (id, length, cantilever_id, steady_arm_type_id, deleted,
                        create_date, create_user, version_date, version_user, version_number)
                select -300000 - n, 1000, -200000 - n, ?, false, now(), 'test', now(), 'test', 1
                  from generate_series(1, ?) n
                """, ARM_TYPE, MANY_PROFILES);
        SqlStatementRecorder.clear();
        long empieza = System.nanoTime();

        trackService.deleteWithEverything(TRACK);

        long milisegundos = (System.nanoTime() - empieza) / 1_000_000;
        List<String> sentencias = SqlStatementRecorder.statements();
        // Fuera queda la lectura con la que el outbox guarda cada evento, que es suya y va por evento.
        long lecturas = sentencias.stream()
                .filter(sql -> sql.stripLeading().startsWith("select"))
                .filter(sql -> !sql.contains("outbox_message") && !sql.contains("nextval"))
                .count();
        System.out.printf("CASCADA perfiles=%d ms=%d sentencias=%d lecturas=%d eventos=%d%n",
                MANY_PROFILES + 2, milisegundos, sentencias.size(), lecturas, eventos().size());

        // Tres filas borradas y tres eventos por perfil de mas: eso no se puede ahorrar.
        assertThat(eventos()).hasSize(9 + 3 * MANY_PROFILES);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from profile where track_id = ? and deleted = false", Integer.class, TRACK))
                .isZero();
        // Las lecturas, si: por lotes de 200 perfiles, no una por perfil ni por mensula.
        assertThat(lecturas).isLessThan(40 + MANY_PROFILES / 100);
    }

    @Test
    @DisplayName("despues, la otra via ya se puede borrar a secas en cuanto no tiene perfiles")
    void noDejaNadaColgando() {
        trackService.deleteWithEverything(TRACK);
        trackService.deleteWithEverything(OTHER_TRACK);

        assertThat(borrada("profile", OTHER_PROFILE)).isTrue();
        // Sin vias enlazadas ni nada suyo, la estacion y el paquete tambien.
        stationService.delete(conId(new StationDTO(), STATION));
        executionPackageService.delete(conId(new ExecutionPackageDTO(), PACKAGE));
        assertThat(vivas()).isZero();
    }

    @Test
    @DisplayName("lo de otra via que la nombra no se borra ni se cambia: impide el borrado, con un 409 que lo dice")
    void loDeOtraViaLoImpide() {
        // En un poste de la otra via, maniobrando esta; un aislador de la otra que conecta con
        // esta; y una aguja de un aislador de la otra que da a esta.
        seccionador(FOREIGN_DISCONNECTOR, "IT-DEL-DISC-FOREIGN", null, OTHER_PROFILE, null, TRACK, null);
        aislador(FOREIGN_INSULATOR, "IT-DEL-SI-FOREIGN", null, OTHER_TRACK, TRACK);
        aguja(FOREIGN_SWITCH, FOREIGN_INSULATOR, TRACK);

        EntityInUseException rechazo = rechazo(() -> trackService.deleteWithEverything(TRACK));

        assertThat(rechazo.getDetail()).contains("vía entera", "1 seccionador, 1 aislador y 1 aguja");
        assertThat(rechazo.getDependents()).containsExactly(
                new Dependent("disconnectors", 1), new Dependent("sectionInsulators", 1),
                new Dependent("sectionInsulatorSwitches", 1));
    }

    @Test
    @DisplayName("una via que no existe es un 404")
    void noExiste() {
        assertThatThrownBy(() -> trackService.deleteWithEverything(-799L))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Un alta a la vez que el borrado de su padre no deja un huerfano ---

    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @AfterEach
    void para() {
        hilos.shutdownNow();
    }

    private ProfileDTO perfilNuevo() {
        ProfileDTO perfil = new ProfileDTO();
        perfil.setProfileId("IT-DEL-RACE");
        perfil.setKp("5.000");
        perfil.setTrackId(OTHER_TRACK);
        ProfileStatusDTO status = new ProfileStatusDTO();
        status.setCode("PROVISIONAL");
        perfil.setProfileStatus(status);
        return perfil;
    }

    /** Ejecuta {@code primero} en una transaccion que no confirma hasta que se le diga. */
    private Future<?> sinConfirmar(Runnable primero, CountDownLatch hecho, CountDownLatch confirmar) {
        return hilos.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            primero.run();
            hecho.countDown();
            try {
                confirmar.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    @Test
    @DisplayName("si el alta va delante, el borrado espera a que confirme, la ve y es un 409")
    void elAltaVaDelante() throws Exception {
        // La otra via, sin nada colgando salvo lo que entre ahora.
        jdbcTemplate.update("update profile set deleted = true where id = ?", OTHER_PROFILE);
        jdbcTemplate.update("update disconnector set deleted = true where id = ?", DISCONNECTOR_ON_POLE);
        CountDownLatch creado = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);

        Future<?> alta = sinConfirmar(() -> profileService.create(perfilNuevo()), creado, confirmar);
        assertThat(creado.await(30, TimeUnit.SECONDS)).isTrue();
        Future<Throwable> borrado = hilos.submit(
                () -> catchThrowable(() -> trackService.delete(conId(new TrackDTO(), OTHER_TRACK))));

        // El borrado no puede contar todavia: el alta no ha confirmado y el recuento no la veria.
        assertThatThrownBy(() -> borrado.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
        confirmar.countDown();
        alta.get(30, TimeUnit.SECONDS);

        assertThat(borrado.get(30, TimeUnit.SECONDS))
                .isInstanceOf(EntityInUseException.class)
                .hasMessageContaining("1 perfil");
        assertThat(borrada("track", OTHER_TRACK)).isFalse();
    }

    @Test
    @DisplayName("si el borrado va delante, el alta espera a que confirme y se rechaza: su via ya no esta")
    void elBorradoVaDelante() throws Exception {
        jdbcTemplate.update("update profile set deleted = true where id = ?", OTHER_PROFILE);
        jdbcTemplate.update("update disconnector set deleted = true where id = ?", DISCONNECTOR_ON_POLE);
        CountDownLatch borrado = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);

        Future<?> baja = sinConfirmar(
                () -> trackService.delete(conId(new TrackDTO(), OTHER_TRACK)), borrado, confirmar);
        assertThat(borrado.await(30, TimeUnit.SECONDS)).isTrue();
        Future<Throwable> alta = hilos.submit(() -> catchThrowable(() -> profileService.create(perfilNuevo())));

        // El alta ha leido la via viva (el borrado no ha confirmado) y espera en su INSERT.
        assertThatThrownBy(() -> alta.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
        confirmar.countDown();
        baja.get(30, TimeUnit.SECONDS);

        assertThat(alta.get(30, TimeUnit.SECONDS)).isInstanceOf(ValidationException.class);
        assertThat(borrada("track", OTHER_TRACK)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from profile where profile_id = 'IT-DEL-RACE'", Integer.class)).isZero();
    }
}
