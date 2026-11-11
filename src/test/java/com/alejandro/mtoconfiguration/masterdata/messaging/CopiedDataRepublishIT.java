package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que copia datos de una via o de una estacion se vuelve a publicar cuando cambian, de punta a
 * punta: los servicios reales contra PostgreSQL, mirando lo que queda en {@code outbox_message}.
 *
 * <p>El evento {@code profile} lleva el paquete de su via ({@code track.executionPackageId}), y
 * {@code mto-maintenance} guarda con el el paquete de cada activo. Pasar una via a otro paquete solo
 * publicaba la via y sus puntos de acceso: los perfiles, los seccionadores y los aisladores seguian
 * alli con el paquete viejo hasta un republicado a mano.
 *
 * <p>El punto de partida, insertado con SQL: dos paquetes; en el primero, dos estaciones y una via
 * (de la primera estacion) con dos perfiles; un seccionador en el poste del primero, otro sin poste
 * con la via como propia, otro que solo es de la estacion, y un aislador en la via.
 */
@SpringBootTest
@DisplayName("Lo que copia datos de su via o de su estacion se vuelve a publicar cuando cambian")
class CopiedDataRepublishIT {

    private static final long PACKAGE = -601L;
    private static final long OTHER_PACKAGE = -602L;
    private static final long STATION = -603L;
    private static final long OTHER_STATION = -604L;
    private static final long TRACK = -605L;
    private static final long PROFILE_A = -606L;
    private static final long PROFILE_B = -607L;
    private static final long ON_POLE = -608L;
    private static final long POLE_LESS = -609L;
    private static final long STATION_ONLY = -610L;
    private static final long INSULATOR = -611L;
    private static final long FUNCTION = -612L;

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
    private ProfileRepository profileRepository;
    @Autowired
    private DisconnectorRepository disconnectorRepository;
    @Autowired
    private SectionInsulatorRepository sectionInsulatorRepository;
    @Autowired
    private MasterDataEventPayloadExtractor payloadExtractor;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /** El ultimo evento que habia antes de la escritura que se prueba. */
    private long marca;

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("insert into disconnector_function (id, code, description, enabled, create_date,"
                + " create_user, version_date, version_user, version_number)"
                + " values (?, 'IT-COPY', 'Datos copiados', true, now(), 'test', now(), 'test', 1)", FUNCTION);
        for (long id : List.of(PACKAGE, OTHER_PACKAGE)) {
            jdbcTemplate.update("""
                    insert into execution_package (id, name, enabled, deleted, initial_package,
                            length, start_date, end_date, company_id, create_date, create_user,
                            version_date, version_user, version_number)
                    values (?, ?, true, false, false, 0, date '2020-01-01', date '2020-12-31',
                            (select min(id) from business_entity), now(), 'test', now(), 'test', 1)
                    """, id, "IT-COPY" + id);
        }
        for (long id : List.of(STATION, OTHER_STATION)) {
            jdbcTemplate.update("""
                    insert into station (id, name, execution_package_id, deleted, create_date,
                            create_user, version_date, version_user, version_number)
                    values (?, ?, ?, false, now(), 'test', now(), 'test', 1)
                    """, id, "IT-COPY-STATION" + id, PACKAGE);
        }
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-COPY-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
        jdbcTemplate.update("insert into track_station (track_id, station_id) values (?, ?)", TRACK, STATION);
        perfil(PROFILE_A, "IT-COPY-A", "1.000");
        perfil(PROFILE_B, "IT-COPY-B", "2.000");
        seccionador(ON_POLE, "IT-COPY-ON-POLE", PROFILE_A, null, null);
        seccionador(POLE_LESS, "IT-COPY-POLE-LESS", null, "1.500", TRACK);
        seccionador(STATION_ONLY, "IT-COPY-STATION-ONLY", null, null, null);
        jdbcTemplate.update("""
                insert into section_insulator (id, name, status, station_id, kilometric_point,
                        installation_type, track_id, deleted, create_date, create_user, version_date,
                        version_user, version_number)
                values (?, 'IT-COPY-SI', true, ?, 1.700, 'IN_TRACK', ?, false, now(), 'test', now(), 'test', 1)
                """, INSULATOR, STATION, TRACK);
        marca = jdbcTemplate.queryForObject(
                "select coalesce(max(sequence_number), 0) from outbox_message", Long.class);
    }

    private void perfil(long id, String code, String kp) {
        jdbcTemplate.update("""
                insert into profile (id, profile_id, kilometric_point, track_id, profile_status_id,
                        deleted, create_date, create_user, version_date, version_user, version_number)
                values (?, ?, ?::numeric, ?, (select id from profile_status where code = 'PROVISIONAL'),
                        false, now(), 'test', now(), 'test', 1)
                """, id, code, kp, TRACK);
    }

    private void seccionador(long id, String name, Long profileId, String kp, Long trackId) {
        jdbcTemplate.update("""
                insert into disconnector (id, name, onload, station_id, disconnector_function_id,
                        profile_id, kilometric_point, track_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, ?, true, ?, ?, ?, ?::numeric, ?, false, now(), 'test', now(), 'test', 1)
                """, id, name, STATION, FUNCTION, profileId, kp, trackId);
    }

    @AfterEach
    void limpia() {
        String packages = "(" + PACKAGE + ", " + OTHER_PACKAGE + ")";
        String tracks = "(select id from track where execution_package_id in " + packages + ")";
        String stations = "(select id from station where execution_package_id in " + packages + ")";
        jdbcTemplate.update("delete from outbox_message where payload like '%IT-COPY%'");
        jdbcTemplate.update("delete from section_insulator_aud where station_id in " + stations);
        jdbcTemplate.update("delete from section_insulator where station_id in " + stations);
        jdbcTemplate.update("delete from disconnector_aud where station_id in " + stations);
        jdbcTemplate.update("delete from disconnector where station_id in " + stations);
        jdbcTemplate.update("delete from profile_aud where track_id in " + tracks);
        jdbcTemplate.update("delete from profile where track_id in " + tracks);
        jdbcTemplate.update("delete from track_station_aud where track_id in " + tracks);
        jdbcTemplate.update("delete from track_station where track_id in " + tracks);
        jdbcTemplate.update("delete from track_aud where execution_package_id in " + packages);
        jdbcTemplate.update("delete from track where execution_package_id in " + packages);
        jdbcTemplate.update("delete from station_aud where execution_package_id in " + packages);
        jdbcTemplate.update("delete from station where execution_package_id in " + packages);
        jdbcTemplate.update("delete from execution_package_aud where id in " + packages);
        jdbcTemplate.update("delete from execution_package where id in " + packages);
        jdbcTemplate.update("delete from disconnector_function_aud where id = ?", FUNCTION);
        jdbcTemplate.update("delete from disconnector_function where id = ?", FUNCTION);
    }

    /** Lo que la escritura ha dejado en el outbox, en su orden: {@code profile.updated#-606}. */
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

    @Test
    @DisplayName("pasar la via a otro paquete publica cada perfil con el paquete nuevo, y detras los seccionadores y el aislador de la via")
    void moverLaViaDePaquete() {
        TrackDTO via = trackService.getById(TRACK);
        via.setExecutionPackageId(OTHER_PACKAGE);

        trackService.update(via);

        assertThat(eventos()).containsExactly(
                "track.updated#" + TRACK,
                "profile.updated#" + PROFILE_B,
                "profile.updated#" + PROFILE_A,
                "disconnector.updated#" + POLE_LESS,
                "disconnector.updated#" + ON_POLE,
                "section-insulator.updated#" + INSULATOR);
        assertThat(values("profile", PROFILE_A).at("/track/executionPackageId").asLong()).isEqualTo(OTHER_PACKAGE);
        assertThat(values("profile", PROFILE_B).at("/track/executionPackageId").asLong()).isEqualTo(OTHER_PACKAGE);
        assertThat(values("track", TRACK).at("/executionPackage/id").asLong()).isEqualTo(OTHER_PACKAGE);
        // Los values de siempre, completos: el del poste dice su perfil, y el de sin poste, su via.
        assertThat(values("disconnector", ON_POLE).at("/profile/id").asLong()).isEqualTo(PROFILE_A);
        assertThat(values("disconnector", POLE_LESS).at("/track/id").asLong()).isEqualTo(TRACK);
        assertThat(values("section-insulator", INSULATOR).at("/track/id").asLong()).isEqualTo(TRACK);
        // Nadie ha escrito sus filas: es el mismo dato, vuelto a contar.
        assertThat(jdbcTemplate.queryForObject("select version_number from profile where id = ?",
                Integer.class, PROFILE_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("devolver la via como estaba, o cambiar solo un perfil suyo, no vuelve a publicar nada")
    void sinCambiosEnLoCopiadoNoSeRepublica() {
        trackService.update(trackService.getById(TRACK));

        assertThat(eventos()).containsExactly("track.updated#" + TRACK);

        marca = jdbcTemplate.queryForObject("select max(sequence_number) from outbox_message", Long.class);
        TrackDTO via = trackService.getById(TRACK);
        via.getProfiles().stream().filter(perfil -> perfil.getId().equals(PROFILE_B)).findFirst().orElseThrow()
                .setSpan(new java.math.BigDecimal("47.970"));

        trackService.update(via);

        assertThat(eventos()).containsExactlyInAnyOrder("track.updated#" + TRACK, "profile.updated#" + PROFILE_B);
    }

    @Test
    @DisplayName("renombrar la via publica sus perfiles y lo que la nombra; el seccionador de un poste, que no la nombra, no")
    void renombrarLaVia() {
        TrackDTO via = trackService.getById(TRACK);
        via.setName("IT-COPY-TRACK-BIS");

        trackService.update(via);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "track.updated#" + TRACK,
                "profile.updated#" + PROFILE_A,
                "profile.updated#" + PROFILE_B,
                "disconnector.updated#" + POLE_LESS,
                "section-insulator.updated#" + INSULATOR);
        assertThat(values("profile", PROFILE_A).at("/track/name").asText()).isEqualTo("IT-COPY-TRACK-BIS");
        assertThat(values("disconnector", POLE_LESS).at("/track/name").asText()).isEqualTo("IT-COPY-TRACK-BIS");
        assertThat(values("section-insulator", INSULATOR).at("/track/name").asText()).isEqualTo("IT-COPY-TRACK-BIS");
    }

    @Test
    @DisplayName("un perfil modificado en el mismo PUT que renombra su via sale una sola vez")
    void loQueYaPublicaLaEscrituraNoSaleDosVeces() {
        TrackDTO via = trackService.getById(TRACK);
        via.setName("IT-COPY-TRACK-BIS");
        via.getProfiles().stream().filter(perfil -> perfil.getId().equals(PROFILE_B)).findFirst().orElseThrow()
                .setSpan(new java.math.BigDecimal("47.970"));

        trackService.update(via);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "track.updated#" + TRACK,
                "profile.updated#" + PROFILE_A,
                "profile.updated#" + PROFILE_B,
                "disconnector.updated#" + POLE_LESS,
                "section-insulator.updated#" + INSULATOR);
        assertThat(values("profile", PROFILE_B).at("/track/name").asText()).isEqualTo("IT-COPY-TRACK-BIS");
        assertThat(values("profile", PROFILE_B).get("span").decimalValue()).isEqualByComparingTo("47.970");
    }

    @Test
    @DisplayName("desactivar la via, o cambiar sus estaciones, publica solo sus perfiles")
    void loQueSoloCopiaElPerfil() {
        TrackDTO via = trackService.getById(TRACK);
        via.setEnabled(false);

        trackService.update(via);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "track.updated#" + TRACK, "profile.updated#" + PROFILE_A, "profile.updated#" + PROFILE_B);
        assertThat(values("profile", PROFILE_A).at("/track/enabled").asBoolean()).isFalse();

        marca = jdbcTemplate.queryForObject("select max(sequence_number) from outbox_message", Long.class);
        via = trackService.getById(TRACK);
        via.setStationIds(new ArrayList<>(List.of(STATION, OTHER_STATION)));

        trackService.update(via);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "track.updated#" + TRACK, "profile.updated#" + PROFILE_A, "profile.updated#" + PROFILE_B);
        assertThat(values("profile", PROFILE_B).at("/track/stationIds")).hasSize(2);
    }

    @Test
    @DisplayName("renombrar la estacion publica sus seccionadores y su aislador, no los perfiles; devolverla como estaba, nada")
    void renombrarLaEstacion() {
        stationService.update(stationService.getById(STATION));

        assertThat(eventos()).containsExactly("station.updated#" + STATION);

        marca = jdbcTemplate.queryForObject("select max(sequence_number) from outbox_message", Long.class);
        StationDTO estacion = stationService.getById(STATION);
        estacion.setName("IT-COPY-STATION-BIS");

        stationService.update(estacion);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "station.updated#" + STATION,
                "disconnector.updated#" + ON_POLE,
                "disconnector.updated#" + POLE_LESS,
                "disconnector.updated#" + STATION_ONLY,
                "section-insulator.updated#" + INSULATOR);
        assertThat(values("disconnector", STATION_ONLY).at("/station/name").asText())
                .isEqualTo("IT-COPY-STATION-BIS");
        assertThat(values("section-insulator", INSULATOR).at("/station/name").asText())
                .isEqualTo("IT-COPY-STATION-BIS");
    }

    @Test
    @DisplayName("una via renombrada dentro del PUT de su paquete tambien vuelve a publicar lo suyo")
    void laViaCambiadaDentroDeSuPaquete() {
        ExecutionPackageDTO paquete = executionPackageService.getById(PACKAGE);
        // El paquete trae cada via dos veces: en «tracks» y dentro de sus estaciones.
        paquete.getTracks().forEach(via -> via.setName("IT-COPY-TRACK-BIS"));
        paquete.getStations().forEach(estacion -> estacion.getTracks()
                .forEach(via -> via.setName("IT-COPY-TRACK-BIS")));

        executionPackageService.update(paquete);

        assertThat(eventos()).containsExactlyInAnyOrder(
                "execution-package.updated#" + PACKAGE,
                "track.updated#" + TRACK,
                "profile.updated#" + PROFILE_A,
                "profile.updated#" + PROFILE_B,
                "disconnector.updated#" + POLE_LESS,
                "section-insulator.updated#" + INSULATOR);
    }

    @Test
    @DisplayName("la consulta de un lote trae todo lo que lee el payload: fuera de la sesion no queda nada perezoso por cargar")
    void elLoteTraeTodoElGrafoDelPayload() {
        assertThat(payloadsFueraDeLaSesion(profileRepository::findAllByIdForMessaging, List.of(PROFILE_A, PROFILE_B)))
                .hasSize(2);
        assertThat(payloadsFueraDeLaSesion(disconnectorRepository::findAllByIdForMessaging,
                List.of(ON_POLE, POLE_LESS, STATION_ONLY))).hasSize(3);
        assertThat(payloadsFueraDeLaSesion(sectionInsulatorRepository::findAllByIdForMessaging, List.of(INSULATOR)))
                .hasSize(1);
    }

    /**
     * Carga el lote en una transaccion y saca los payloads <b>despues</b>, con la sesion cerrada: una
     * relacion que la consulta no trajese (y que en el republicado costaria una sentencia por
     * elemento) es aqui una {@code LazyInitializationException} que dice cual falta.
     */
    private <E extends IEntity> List<Map<String, Object>> payloadsFueraDeLaSesion(
            Function<Collection<Long>, List<E>> loader, List<Long> ids) {
        List<E> loaded = transactionTemplate.execute(status -> loader.apply(ids));

        return loaded.stream().map(payloadExtractor::extract).toList();
    }
}
