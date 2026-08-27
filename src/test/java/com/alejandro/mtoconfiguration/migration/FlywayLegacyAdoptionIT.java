package com.alejandro.mtoconfiguration.migration;

import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Adopcion de Flyway sobre una base que YA existia, que es el camino de verdad
 * arriesgado: en el otro, la base esta vacia y V1 lo crea todo bien por definicion.
 * <p>
 * Se reproduce el outbox_message tal y como lo dejaba Hibernate antes de este
 * trabajo (payload como large object y un CHECK de estado sin IN_PROGRESS), se
 * marca V1 como ya aplicada con baseline y se comprueba que V2 y V3 lo ponen al dia
 * SIN perder el contenido de los mensajes que estuvieran a medio publicar.
 */
class FlywayLegacyAdoptionIT {

    private static final String SCHEMA = "flyway_legacy_it";
    private static final String PAYLOAD = "{\"entityName\":\"station\",\"id\":42}";

    /** OID del large object creado antes de migrar, para comprobar que se libera. */
    private static long largeObjectId;

    @BeforeAll
    static void migrateLegacySchema() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema if exists " + SCHEMA + " cascade");
            statement.execute("create schema " + SCHEMA);
            statement.execute("set search_path to " + SCHEMA);

            // outbox_message como lo dejaba Hibernate antes de este trabajo
            statement.execute("""
                    create table outbox_message (
                        id uuid not null primary key,
                        aggregate_type varchar(100) not null,
                        aggregate_id varchar(150) not null,
                        event_type varchar(150) not null,
                        exchange_name varchar(200) not null,
                        routing_key varchar(200) not null,
                        payload oid not null,
                        status varchar(30) not null check (status in ('PENDING','PUBLISHED','FAILED')),
                        attempts integer not null,
                        max_attempts integer not null,
                        created_at timestamp(6) with time zone not null,
                        next_attempt_at timestamp(6) with time zone,
                        published_at timestamp(6) with time zone,
                        last_error varchar(1000)
                    )
                    """);

            // Un mensaje a medio publicar: su contenido no se puede perder
            statement.execute("""
                    insert into outbox_message
                        (id, aggregate_type, aggregate_id, event_type, exchange_name, routing_key,
                         payload, status, attempts, max_attempts, created_at)
                    values (gen_random_uuid(), 'station', '42', 'MASTER_DATA_STATION_CREATED',
                            'mto.master-data.exchange', 'mto.master-data.station.created',
                            lo_from_bytea(0, convert_to('%s', 'UTF8')), 'PENDING', 0, 20, now())
                    """.formatted(PAYLOAD));

            try (ResultSet resultSet = statement.executeQuery("select payload from outbox_message")) {
                resultSet.next();
                largeObjectId = resultSet.getLong(1);
            }
        }

        Flyway.configure()
                .dataSource(PostgresTestDatabase.url(), PostgresTestDatabase.username(), PostgresTestDatabase.password())
                .locations("classpath:db/migration")
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                // Lo que hace posible adoptar Flyway aqui: da V1 por aplicada sin
                // ejecutarla, porque el esquema ya existe, y sigue por V2.
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
    }

    @Test
    void v1NoSeEjecutaYLasSiguientesSi() throws SQLException {
        // Esta lista crece con cada migracion nueva. Es a proposito que haya que tocarla: obliga a
        // comprobar que la migracion tambien se aplica limpiamente sobre un esquema PREEXISTENTE,
        // que es el caso que este test cubre y el unico donde V1 no se ejecuta.
        assertThat(appliedVersions()).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(queryForString(
                "select type from " + SCHEMA + ".flyway_schema_history where version = '1'"))
                .as("V1 debe quedar marcada como baseline, no ejecutada sobre un esquema que ya existe")
                .isEqualTo("BASELINE");
    }

    @Test
    void elPayloadPasaDeLargeObjectATextSinPerderElContenido() throws SQLException {
        assertThat(queryForString("""
                select data_type from information_schema.columns
                where table_schema = '%s' and table_name = 'outbox_message' and column_name = 'payload'
                """.formatted(SCHEMA))).isEqualTo("text");

        assertThat(queryForString("select payload from " + SCHEMA + ".outbox_message"))
                .as("un mensaje sin publicar no puede perder su contenido en la migracion")
                .isEqualTo(PAYLOAD);
    }

    @Test
    void elLargeObjectAntiguoNoQuedaHuerfano() throws SQLException {
        // Si el ALTER se hiciera sin desreferenciarlo, el contenido seguiria ocupando
        // sitio en pg_largeobject para siempre, invisible desde la tabla y sin que
        // ningun borrado de filas lo recupere jamas.
        assertThat(queryForString(
                "select count(*) from pg_largeobject_metadata where oid = " + largeObjectId))
                .as("el large object %s deberia haberse liberado al convertir la columna", largeObjectId)
                .isEqualTo("0");
    }

    @Test
    void elEstadoInProgressPasaAEstarPermitido() {
        // El CHECK viejo lo rechazaba, y ddl-auto: validate no mira los CHECK: sin la
        // migracion, el fallo saldria en la primera pasada del relay, en produccion.
        assertThatCode(() -> execute("""
                insert into %s.outbox_message
                    (id, aggregate_type, aggregate_id, event_type, exchange_name, routing_key,
                     payload, status, attempts, max_attempts, created_at)
                values (gen_random_uuid(), 'station', '43', 'X', 'e', 'r', '{}', 'IN_PROGRESS', 0, 20, now())
                """.formatted(SCHEMA)))
                .doesNotThrowAnyException();
    }

    @Test
    void losIndicesDelOutboxQuedanCreados() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select indexname from pg_indexes where schemaname = '" + SCHEMA + "'")) {

            StringBuilder indices = new StringBuilder();
            while (resultSet.next()) {
                indices.append(resultSet.getString(1)).append(" ");
            }

            assertThat(indices.toString())
                    .contains("idx_outbox_message_claim")
                    .contains("idx_outbox_message_purge")
                    .contains("idx_outbox_message_failed");
        }
    }

    private static java.util.List<String> appliedVersions() throws SQLException {
        java.util.List<String> versions = new java.util.ArrayList<>();
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select version from " + SCHEMA + ".flyway_schema_history"
                             + " where success and version is not null order by installed_rank")) {
            while (resultSet.next()) {
                versions.add(resultSet.getString(1));
            }
        }
        return versions;
    }

    private static String queryForString(String sql) throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestDatabase.url(), PostgresTestDatabase.username(), PostgresTestDatabase.password());
    }
}
