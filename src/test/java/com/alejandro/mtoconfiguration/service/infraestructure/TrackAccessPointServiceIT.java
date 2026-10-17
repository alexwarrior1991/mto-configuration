package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
 * El punto de acceso a la vía (V28) de punta a punta: el servicio real contra PostgreSQL, con el
 * esquema de las migraciones y el outbox.
 *
 * <p>Lo que solo se ve aquí: que cada escritura deja su evento {@code track-access-point} en el outbox
 * en la misma transacción, con la clave de enrutado a la que se liga la cola de {@code mto-resources} y
 * los {@code values} del contrato; que el borrado es lógico y publica {@code DELETED}; que la versión
 * leída manda (409 {@code CON-001}); y que el nombre es único dentro de su vía salvo lo borrado (el
 * índice parcial de V28, que sale como 409 {@code BUS-002}).
 *
 * <p>La vía y la estación se insertan con SQL, como en {@code FilterOutsideTransactionIT}; lo que se
 * prueba se escribe por el servicio, que es por donde entra una petición.
 */
@SpringBootTest
@DisplayName("El punto de acceso a la vía, de punta a punta")
class TrackAccessPointServiceIT {

    private static final long PACKAGE = -281L;
    private static final long STATION = -282L;
    private static final long TRACK = -283L;
    private static final String PREFIX = "IT-TAP";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TrackAccessPointService trackAccessPointService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void inserta() {
        limpia();
        jdbcTemplate.update("""
                insert into execution_package (id, name, enabled, deleted, initial_package,
                        length, start_date, end_date, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-TAP', true, false, false, 0, date '2020-01-01',
                        date '2020-12-31', now(), 'test', now(), 'test', 1)
                """, PACKAGE);
        jdbcTemplate.update("""
                insert into station (id, name, execution_package_id, deleted, create_date, create_user,
                        version_date, version_user, version_number)
                values (?, 'IT-TAP-STATION', ?, false, now(), 'test', now(), 'test', 1)
                """, STATION, PACKAGE);
        jdbcTemplate.update("""
                insert into track (id, name, status, deleted, execution_package_id, create_date,
                        create_user, version_date, version_user, version_number)
                values (?, 'IT-TAP-TRACK', true, false, ?, now(), 'test', now(), 'test', 1)
                """, TRACK, PACKAGE);
    }

    @AfterEach
    void limpia() {
        jdbcTemplate.update("""
                delete from outbox_message
                 where aggregate_type = 'track-access-point'
                   and aggregate_id in (select id::text from track_access_point where name like 'IT-TAP%')
                """);
        jdbcTemplate.update("delete from track_access_point_aud where id in "
                + "(select id from track_access_point where name like 'IT-TAP%')");
        jdbcTemplate.update("delete from track_access_point where name like 'IT-TAP%'");
        jdbcTemplate.update("delete from track where id = ?", TRACK);
        jdbcTemplate.update("delete from station where id = ?", STATION);
        jdbcTemplate.update("delete from execution_package where id = ?", PACKAGE);
    }

    private static TrackAccessPointDTO acceso(String name) {
        TrackAccessPointDTO dto = new TrackAccessPointDTO();
        dto.setName(name);
        dto.setTrackId(TRACK);
        dto.setStationId(STATION);
        dto.setKp(new BigDecimal("110176.000"));
        dto.setKind(TrackAccessPointKind.PREPARED_ACCESS);
        dto.setSide(TrackAccessPointSide.LEFT);
        dto.setMaxAxleLoadT(new BigDecimal("12.500"));
        dto.setMaxGrossWeightT(new BigDecimal("40.000"));
        dto.setLatitude(new BigDecimal("31.669342"));
        dto.setLongitude(new BigDecimal("34.574119"));
        return dto;
    }

    /** Los eventos del acceso en el orden en que se escribieron, con su clave y su payload. */
    private List<Map<String, Object>> eventos(Long id) {
        return jdbcTemplate.queryForList("""
                select event_type, routing_key, payload from outbox_message
                 where aggregate_type = 'track-access-point' and aggregate_id = ?
                 order by sequence_number
                """, String.valueOf(id));
    }

    private JsonNode values(Map<String, Object> evento) {
        return objectMapper.readTree((String) evento.get("payload")).at("/data/values");
    }

    @Test
    @DisplayName("el alta guarda el acceso y deja su evento CREATED con los values del contrato")
    void altaPublicaElEvento() {
        TrackAccessPointDTO creado = trackAccessPointService.create(acceso(PREFIX + "-A"));

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getVersionNumber()).isEqualTo(1);
        assertThat(creado.getTrackId()).isEqualTo(TRACK);
        assertThat(creado.getRoadRailAccess()).isTrue();

        assertThat(eventos(creado.getId())).singleElement().satisfies(evento -> {
            assertThat(evento.get("event_type")).isEqualTo("MASTER_DATA_TRACK_ACCESS_POINT_CREATED");
            assertThat(evento.get("routing_key")).isEqualTo("mto.master-data.track-access-point.created");

            JsonNode values = values(evento);
            assertThat(values.get("id").asLong()).isEqualTo(creado.getId());
            assertThat(values.get("kind").asText()).isEqualTo("PREPARED_ACCESS");
            assertThat(values.get("side").asText()).isEqualTo("LEFT");
            assertThat(values.get("roadRailAccess").asBoolean()).isTrue();
            assertThat(values.get("kp").decimalValue()).isEqualByComparingTo("110176");
            assertThat(values.get("maxGrossWeightT").decimalValue()).isEqualByComparingTo("40");
            assertThat(values.get("latitude").decimalValue()).isEqualByComparingTo("31.669342");
            assertThat(values.at("/station/name").asText()).isEqualTo("IT-TAP-STATION");
            assertThat(values.at("/track/name").asText()).isEqualTo("IT-TAP-TRACK");
            assertThat(values.at("/track/executionPackageId").asLong()).isEqualTo(PACKAGE);
        });
    }

    @Test
    @DisplayName("modificar con la versión leída escribe y publica UPDATED; con una vieja, 409 sin escribir nada")
    void modificarConLaVersionLeida() {
        TrackAccessPointDTO creado = trackAccessPointService.create(acceso(PREFIX + "-B"));

        TrackAccessPointDTO leido = trackAccessPointService.getById(creado.getId());
        TrackAccessPointDTO viejo = trackAccessPointService.getById(creado.getId());

        leido.setRoadRailAccess(false);
        leido.setStationId(null);
        TrackAccessPointDTO modificado = trackAccessPointService.update(leido);

        assertThat(modificado.getVersionNumber()).isEqualTo(2);
        assertThat(modificado.getStationId()).isNull();
        assertThat(eventos(creado.getId())).hasSize(2).last().satisfies(evento -> {
            assertThat(evento.get("routing_key")).isEqualTo("mto.master-data.track-access-point.updated");
            assertThat(values(evento).get("roadRailAccess").asBoolean()).isFalse();
            assertThat(values(evento).get("station").isNull()).isTrue();
        });

        viejo.setName(PREFIX + "-PISADO");
        assertThatThrownBy(() -> trackAccessPointService.update(viejo)).isInstanceOf(ConcurrencyException.class);
        assertThat(trackAccessPointService.getById(creado.getId()).getName()).isEqualTo(PREFIX + "-B");
    }

    @Test
    @DisplayName("borrar es lógico: la fila se queda marcada y se publica DELETED")
    void borrarEsLogico() {
        TrackAccessPointDTO creado = trackAccessPointService.create(acceso(PREFIX + "-C"));

        TrackAccessPointDTO porId = new TrackAccessPointDTO();
        porId.setId(creado.getId());
        trackAccessPointService.delete(porId);

        assertThat(jdbcTemplate.queryForObject("select deleted from track_access_point where id = ?",
                Boolean.class, creado.getId())).isTrue();
        assertThat(trackAccessPointService.getTrackAccessPointsByTrackId(TRACK)).isEmpty();
        assertThat(eventos(creado.getId())).last()
                .satisfies(evento -> assertThat(evento.get("routing_key"))
                        .isEqualTo("mto.master-data.track-access-point.deleted"));
    }

    @Test
    @DisplayName("el nombre es único dentro de su vía, sin contar lo borrado")
    void nombreUnicoEnLaVia() {
        TrackAccessPointDTO primero = trackAccessPointService.create(acceso(PREFIX + "-D"));

        assertThatThrownBy(() -> trackAccessPointService.create(acceso("it-tap-d")))
                .isInstanceOf(DataIntegrityViolationException.class);

        TrackAccessPointDTO porId = new TrackAccessPointDTO();
        porId.setId(primero.getId());
        trackAccessPointService.delete(porId);

        assertThat(trackAccessPointService.create(acceso(PREFIX + "-D")).getId()).isNotEqualTo(primero.getId());
    }

    @Test
    @DisplayName("una latitud sin longitud es un 400 con su campo, antes de llegar a la base")
    void coordenadaSola() {
        TrackAccessPointDTO dto = acceso(PREFIX + "-E");
        dto.setLongitude(null);

        assertThatThrownBy(() -> trackAccessPointService.create(dto))
                .isInstanceOf(ValidationException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from track_access_point where name = ?",
                Integer.class, PREFIX + "-E")).isZero();
    }
}
