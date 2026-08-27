package com.alejandro.mtoconfiguration.migration;

import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Aplica las migraciones sobre un schema VACIO y deja que Hibernate valide el
 * resultado con ddl-auto: validate.
 * <p>
 * Que el contexto arranque ya es la afirmacion principal del test: significa que
 * V1 reproduce exactamente lo que las entidades esperan. Sin esto, la migracion y
 * las entidades se separan en silencio y el desajuste aparece al desplegar, que es
 * el momento mas caro para enterarse.
 * <p>
 * El resto de comprobaciones cubren lo que ddl-auto: validate NO mira: los indices
 * y las restricciones CHECK.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// El slice de @DataJpaTest no trae Flyway: hay que pedirlo explicitamente.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlywayMigrationIT {

    /** Schema propio y recreado de cero: las migraciones deben partir de vacio. */
    private static final String SCHEMA = "flyway_migration_it";

    static {
        recreateSchema();
    }

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @DynamicPropertySource
    static void migrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestDatabase::url);
        registry.add("spring.datasource.username", PostgresTestDatabase::username);
        registry.add("spring.datasource.password", PostgresTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Aqui SI queremos Flyway: es lo que se esta probando.
        registry.add("spring.flyway.enabled", () -> "true");
        // application.yaml da a Flyway su propia url/user/password, asi que no basta
        // con apuntar el datasource: sin esto Flyway migraria OTRA base de datos.
        registry.add("spring.flyway.url", PostgresTestDatabase::url);
        registry.add("spring.flyway.user", PostgresTestDatabase::username);
        registry.add("spring.flyway.password", PostgresTestDatabase::password);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-version", () -> "1");

        // Y Hibernate solo valida, como en produccion.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @Test
    void lasMigracionesSeAplicanEnOrdenYSinFallos() {
        // Se filtra por type SQL: create-schemas anade ademas una fila SCHEMA sin version.
        List<String> versiones = jdbc().queryForList(
                "select version from " + SCHEMA + ".flyway_schema_history"
                        + " where success and type = 'SQL' order by installed_rank",
                String.class);

        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void elEsquemaMigradoCuadraConLasEntidades() {
        // Si V1 se hubiera separado de las entidades, el contexto no habria
        // arrancado: ddl-auto: validate falla antes de llegar hasta aqui.
        Integer tablas = jdbc().queryForObject(
                "select count(*) from information_schema.tables where table_schema = ?",
                Integer.class, SCHEMA);

        assertThat(tablas)
                .as("el schema migrado deberia tener las tablas de negocio, las _AUD de Envers y el historico de Flyway")
                .isGreaterThan(50);
    }

    @Test
    void elPayloadDelOutboxEsTextYNoUnLargeObject() {
        String tipo = jdbc().queryForObject(
                """
                select data_type from information_schema.columns
                where table_schema = ? and table_name = 'outbox_message' and column_name = 'payload'
                """, String.class, SCHEMA);

        // Con oid, borrar una fila deja el contenido huerfano en pg_largeobject y la
        // purga adelgazaria la tabla mientras la base de datos sigue engordando.
        assertThat(tipo).isEqualTo("text");
    }

    @Test
    void elOutboxGuardaElContextoDeTraza() {
        List<String> columnas = jdbc().queryForList(
                """
                select column_name from information_schema.columns
                where table_schema = ? and table_name = 'outbox_message'
                """, String.class, SCHEMA);

        // Sin estas columnas la traza se parte en el salto del outbox: el span de
        // publicacion cuelga del scheduler y no de la operacion que lo origino.
        assertThat(columnas).contains("trace_parent", "trace_state");
    }

    @Test
    void elContextoDeTrazaEsOpcional() {
        // Los mensajes anteriores a la migracion no lo tienen, y tampoco lo tendran los
        // eventos generados fuera de una peticion trazada (una tarea programada).
        List<String> obligatorias = jdbc().queryForList(
                """
                select column_name from information_schema.columns
                where table_schema = ? and table_name = 'outbox_message'
                  and column_name in ('trace_parent', 'trace_state') and is_nullable = 'NO'
                """, String.class, SCHEMA);

        assertThat(obligatorias).isEmpty();
    }

    @Test
    void elOutboxTieneSusIndicesParciales() {
        List<String> indices = jdbc().queryForList(
                "select indexname from pg_indexes where schemaname = ? and tablename = 'outbox_message'",
                String.class, SCHEMA);

        assertThat(indices)
                .contains("idx_outbox_message_claim", "idx_outbox_message_purge",
                        "idx_outbox_message_failed", "idx_outbox_message_aggregate");
    }

    @Test
    void elEstadoInProgressEsAceptadoPorLaRestriccionCheck() {
        // ddl-auto: validate no mira las restricciones CHECK. Una base creada antes
        // de que existiera IN_PROGRESS lo rechaza en la primera pasada del relay.
        assertThatCode(() -> jdbc().update(
                """
                insert into %s.outbox_message
                    (id, aggregate_type, aggregate_id, event_type, exchange_name, routing_key,
                     payload, status, attempts, max_attempts, created_at)
                values (gen_random_uuid(), 'station', '1', 'X', 'e', 'r', '{}', 'IN_PROGRESS', 0, 20, now())
                """.formatted(SCHEMA)))
                .doesNotThrowAnyException();

        jdbc().update("delete from " + SCHEMA + ".outbox_message");
    }

    @Test
    void laBaseDeDatosAsignaElNumeroDeSecuencia() {
        // El DEFAULT vive en la migracion, no en el mapeo: es la base quien reparte el
        // contador, porque con varias replicas escribiendo es el unico sitio donde de
        // verdad es unico y creciente.
        jdbc().update("""
                insert into %s.outbox_message
                    (id, aggregate_type, aggregate_id, event_type, exchange_name, routing_key,
                     payload, status, attempts, max_attempts, created_at)
                values (gen_random_uuid(), 'station', '1', 'X', 'e', 'r', '{}', 'PENDING', 0, 20, now())
                """.formatted(SCHEMA));

        assertThat(jdbc().queryForObject(
                "select sequence_number from " + SCHEMA + ".outbox_message", Long.class))
                .isNotNull()
                .isPositive();

        jdbc().update("delete from " + SCHEMA + ".outbox_message");
    }

    @Test
    void laConsultaDeReclamoPuedeUsarSusIndices() {
        // Con la tabla casi vacia el planificador elige seq scan por tamano, no por
        // falta de indice. Desactivandolo se comprueba lo que interesa: que los
        // indices parciales SIRVEN para la consulta tal y como esta escrita.
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("set enable_seqscan = off");

        String plan = String.join("\n", jdbc.queryForList(
                """
                explain select * from %1$s.outbox_message o
                where o.status in ('PENDING', 'IN_PROGRESS')
                  and coalesce(o.next_attempt_at, o.created_at) <= now()
                  and not exists (
                      select 1 from %1$s.outbox_message anterior
                      where anterior.aggregate_type = o.aggregate_type
                        and anterior.aggregate_id = o.aggregate_id
                        and anterior.status in ('PENDING', 'IN_PROGRESS')
                        and anterior.sequence_number < o.sequence_number
                  )
                order by o.sequence_number
                limit 50
                """.formatted(SCHEMA), String.class));

        jdbc.execute("set enable_seqscan = on");

        // Se comprueba que la consulta es INDEXABLE, no cual de los dos indices gana:
        // con la tabla vacia el planificador no elige lo mismo que elegiria con
        // millones de filas, y fijar el indice concreto seria un test que miente.
        // Lo que no puede aparecer nunca es un recorrido de la tabla entera.
        assertThat(plan)
                .as("sin indice, cada pasada del relay recorre outbox_message entera")
                .contains("idx_outbox_message_")
                .doesNotContain("Seq Scan on outbox_message");
    }

    private static void recreateSchema() {
        try (Connection connection = DriverManager.getConnection(
                PostgresTestDatabase.url(), PostgresTestDatabase.username(), PostgresTestDatabase.password());
             Statement statement = connection.createStatement()) {

            statement.execute("drop schema if exists " + SCHEMA + " cascade");
        } catch (SQLException exception) {
            throw new IllegalStateException("No se ha podido preparar el schema de migracion", exception);
        }
    }
}
