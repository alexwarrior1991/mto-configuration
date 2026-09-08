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
import java.util.Map;

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

        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    }

    /**
     * V9 amplia LOV.code a varchar(40).
     *
     * <p>Se comprueba aqui porque {@code ddl-auto: validate} <b>no</b> mira las longitudes
     * de varchar: si la migracion y la anotacion {@code @Size} de
     * {@link com.alejandro.mtoconfiguration.entity.lov.commons.Lov} se separasen, la
     * aplicacion arrancaria tan tranquila y el fallo saldria al insertar el primer codigo
     * largo del catalogo, como {@code CP/TX-P/1100} o {@code 2HEB-300 V (CP)}.
     */
    @Test
    void elCodigoDeLasLovAceptaLosCodigosLargosDelCatalogo() {
        List<String> cortas = jdbc().queryForList(
                "select table_name from information_schema.columns"
                        + " where table_schema = ? and column_name = 'code'"
                        + " and character_maximum_length < 40 order by table_name",
                String.class, SCHEMA);

        assertThat(cortas)
                .as("ninguna tabla con columna code deberia quedarse por debajo de 40")
                .isEmpty();
    }

    /**
     * V9 anade unicidad por codigo en las 16 tablas LOV base, pero NO en sus gemelas _aud.
     *
     * <p>Sin el indice unico, reimportar el catalogo duplicaria filas y
     * {@code LovRepository.findByCode}, que devuelve un unico resultado, reventaria con
     * {@code NonUniqueResultException}. En las tablas _aud el indice seria justo lo
     * contrario de lo que hace falta: Envers guarda una fila por revision.
     */
    @Test
    void elCodigoDeLasLovEsUnicoEnLasTablasBasePeroNoEnLasDeAuditoria() {
        List<String> conIndiceUnico = jdbc().queryForList(
                "select tablename from pg_indexes"
                        + " where schemaname = ? and indexname like 'ux_%_code' order by tablename",
                String.class, SCHEMA);

        assertThat(conIndiceUnico)
                .hasSize(16)
                .contains("foundation", "pole_type", "sectioning", "anchorage")
                .as("las tablas _aud guardan una fila por revision: alli el codigo se repite")
                .noneMatch(tabla -> tabla.endsWith("_aud"));
    }

    /**
     * V10 amplia el CHECK de {@code async_job.job_type} con {@code LOV_IMPORT}.
     *
     * <p>Mismo motivo que {@link #elEstadoDeUnTrabajoEstaAcotadoPorLaBaseDeDatos()}: los CHECK
     * no los valida Hibernate, asi que sin la migracion el fallo aparece al lanzar la primera
     * importacion, no al arrancar.
     */
    @Test
    void elTipoDeTrabajoAdmiteLaImportacionDeLov() {
        assertThatCode(() -> jdbc().update(insertAsyncJob("LOV_IMPORT", "PENDING")))
                .doesNotThrowAnyException();
    }

    /**
     * V11 anade a profile los cuatro campos tecnicos y la clave ajena de sectioning_feeding.
     *
     * <p>El tipo si lo mira {@code ddl-auto: validate}, pero <b>la gemela de auditoria no</b>:
     * Envers no participa en esa validacion, asi que si {@code profile_aud} se quedara sin estas
     * columnas la aplicacion arrancaria igual y el fallo saldria al guardar la primera revision de
     * un perfil, con el dato ya perdido para el historico.
     */
    @Test
    void elPerfilYSuGemelaDeAuditoriaTienenLosCamposTecnicos() {
        List<String> esperadas = List.of("span", "height_cantilever_support", "pole_gauge_location",
                "rail_pole_distance", "sectioning_feeding_id");

        for (String tabla : List.of("profile", "profile_aud")) {
            List<String> columnas = jdbc().queryForList(
                    "select column_name from information_schema.columns"
                            + " where table_schema = ? and table_name = ?",
                    String.class, SCHEMA, tabla);

            assertThat(columnas).as(tabla).containsAll(esperadas);
        }
    }

    /**
     * {@code sectioningFeeding} reutiliza el catalogo DisconnectorFunction en lugar de tener una
     * LOV propia. Sin la clave ajena, un codigo inexistente se guardaria como un id huerfano y el
     * evento de datos maestros saldria con una referencia rota.
     */
    @Test
    void laAlimentacionDelPerfilApuntaAlCatalogoDeFuncionesDeSeccionador() {
        List<String> referenciadas = jdbc().queryForList(
                """
                select ccu.table_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema
                where tc.table_schema = ? and tc.table_name = 'profile'
                  and tc.constraint_type = 'FOREIGN KEY'
                  and kcu.column_name = 'sectioning_feeding_id'
                """, String.class, SCHEMA);

        assertThat(referenciadas).containsExactly("disconnector_function");
    }

    /**
     * V12 anade los indices unicos que hacen idempotente la importacion masiva.
     *
     * <p>El {@code WHERE deleted = false} no es un detalle: estas cuatro tablas llevan
     * borrado logico, asi que un indice unico plano chocaria con las filas ya borradas e
     * impediria volver a dar de alta una via que se borro en su dia.
     */
    @Test
    void lasClavesNaturalesDeInfraestructuraSonUnicasSalvoLoBorrado() {
        Map<String, String> indices = new java.util.HashMap<>();
        jdbc().query("select indexname, indexdef from pg_indexes"
                        + " where schemaname = ? and indexname like 'ux_%'", rs -> {
            indices.put(rs.getString("indexname"), rs.getString("indexdef"));
        }, SCHEMA);

        for (String nombre : List.of("ux_execution_package_name", "ux_station_ep_name",
                "ux_track_ep_name", "ux_profile_track_profile_id")) {
            assertThat(indices).containsKey(nombre);
            assertThat(indices.get(nombre))
                    .as("%s tiene que ser UNIQUE y parcial", nombre)
                    .contains("UNIQUE")
                    .contains("deleted = false");
        }
    }

    /**
     * V12 hace opcional la longitud del brazo.
     *
     * <p>5.691 de las 14.592 mensulas de los workbooks traen el tipo pero no la longitud.
     * No es un dato que falte por descuido: no se conoce.
     */
    /**
     * V14: el seccionamiento pasa a N:M porque un perfil puede llevar varios a la vez.
     *
     * <p>Lo que se comprueba aqui es que la columna vieja YA NO ESTA. Dejarla seria peor que
     * no migrar: habria dos sitios donde mirar y ninguna garantia de que digan lo mismo.
     */
    @Test
    void elSeccionamientoEsAhoraUnaTablaDeUnion() {
        assertThat(existeTabla("profile_sectioning")).isTrue();
        assertThat(existeColumna("profile", "sectioning_id")).isFalse();
        assertThat(existeColumna("profile_aud", "sectioning_id")).isFalse();

        assertThat(jdbc().queryForObject(
                "select count(*) from information_schema.table_constraints"
                        + " where table_schema = ? and table_name = 'profile_sectioning'"
                        + " and constraint_type = 'PRIMARY KEY'", Integer.class, SCHEMA))
                .isEqualTo(1);
    }

    /**
     * Envers audita la PERTENENCIA de una N:M, no la entidad del otro lado (Sectioning es un
     * catalogo, NOT_AUDITED). Sin esta gemela la aplicacion no arranca con ddl-auto: validate.
     */
    @Test
    void laTablaDeUnionTieneGemelaDeAuditoria() {
        assertThat(existeTabla("profile_sectioning_aud")).isTrue();
        assertThat(existeColumna("profile_sectioning_aud", "rev")).isTrue();
        assertThat(existeColumna("profile_sectioning_aud", "revtype")).isTrue();
        assertThat(existeColumna("profile_sectioning_aud", "profile_id")).isTrue();
        assertThat(existeColumna("profile_sectioning_aud", "sectioning_id")).isTrue();
    }

    @Test
    void laLongitudDelBrazoAdmiteNulo() {
        String nullable = jdbc().queryForObject(
                "select is_nullable from information_schema.columns"
                        + " where table_schema = ? and table_name = 'steady_arm'"
                        + " and column_name = 'length'",
                String.class, SCHEMA);

        assertThat(nullable).isEqualTo("YES");
    }

    /**
     * V12 siembra profile_status, que estaba VACIO.
     *
     * <p>Sin estas tres filas el fallo es silencioso: {@code ProfileValidator} exige
     * {@code profileStatus}, mandar {@code {"code":"DEFINITIVE"}} pasa la validacion,
     * {@code MasterDataService} resuelve el codigo a null sin quejarse y el perfil se
     * guarda con {@code profile_status_id} nulo.
     */
    @Test
    void elCatalogoDeEstadosDePerfilEstaSembrado() {
        List<String> codigos = jdbc().queryForList(
                "select code from " + SCHEMA + ".profile_status order by code", String.class);

        assertThat(codigos).containsExactly("DEFINITIVE", "DRAFT", "PROVISIONAL");
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
    void losTrabajosEnSegundoPlanoTienenSuTabla() {
        List<String> columnas = jdbc().queryForList(
                """
                select column_name from information_schema.columns
                where table_schema = ? and table_name = 'async_job'
                """, String.class, SCHEMA);

        // Si la tabla se hubiera separado de la entidad, el contexto no habria arrancado:
        // ddl-auto: validate falla antes de llegar hasta aqui. Lo que se comprueba es que estan
        // las columnas de las que depende el reparto de cupo.
        assertThat(columnas).contains("job_type", "status", "heartbeat_at", "file_name",
                "processed_items", "successful_items", "failed_items", "error_details_json");
    }

    @Test
    void elLatidoEsObligatorio() {
        List<String> opcionales = jdbc().queryForList(
                """
                select column_name from information_schema.columns
                where table_schema = ? and table_name = 'async_job'
                  and column_name = 'heartbeat_at' and is_nullable = 'YES'
                """, String.class, SCHEMA);

        // Un latido nulo seria un trabajo que nace muerto para el reparto de cupo, y el fallo no
        // se veria: simplemente dejaria de contar y el tope se relajaria en silencio.
        assertThat(opcionales).isEmpty();
    }

    @Test
    void losTrabajosTienenSusIndicesParciales() {
        List<String> indices = jdbc().queryForList(
                "select indexname from pg_indexes where schemaname = ? and tablename = 'async_job'",
                String.class, SCHEMA);

        // Parciales, como los del outbox: async_job solo crece, pero los trabajos vivos son
        // siempre un punado y el indice se mantiene diminuto aunque se acumulen millones.
        assertThat(indices).contains("idx_async_job_slot", "idx_async_job_purge",
                "idx_async_job_active", "idx_async_job_created_at");
    }

    @Test
    void elEstadoDeUnTrabajoEstaAcotadoPorLaBaseDeDatos() {
        // ddl-auto: validate NO comprueba los CHECK, asi que si la migracion se olvidara de
        // ampliarlos al anadir un estado nuevo, el fallo saldria en el primer INSERT en produccion.
        // OJO: la SQL se compone concatenando y no con un bloque de texto. En un text block
        // de Java los espacios FINALES se eliminan, asi que "insert into " se quedaba en
        // "insert into" y la sentencia moria de un error de sintaxis antes de llegar al CHECK.
        // El test pasaba igual (solo exigia "que lance algo") y habria seguido pasando con la
        // restriccion borrada, que es justo lo que venia a comprobar.
        assertThatCode(() -> jdbc().update(insertAsyncJob("PROFILE_EXPORT", "ESTADO_INVENTADO")))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("async_job_status_check");
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

    /**
     * INSERT minimo en {@code async_job}. Compuesto por concatenacion a proposito: ver la
     * nota de {@link #elEstadoDeUnTrabajoEstaAcotadoPorLaBaseDeDatos()}.
     */
    private boolean existeTabla(String tabla) {
        Integer n = jdbc().queryForObject(
                "select count(*) from information_schema.tables"
                        + " where table_schema = ? and table_name = ?",
                Integer.class, SCHEMA, tabla);
        return n != null && n > 0;
    }

    private boolean existeColumna(String tabla, String columna) {
        Integer n = jdbc().queryForObject(
                "select count(*) from information_schema.columns"
                        + " where table_schema = ? and table_name = ? and column_name = ?",
                Integer.class, SCHEMA, tabla, columna);
        return n != null && n > 0;
    }

    private String insertAsyncJob(String jobType, String status) {
        return "insert into " + SCHEMA + ".async_job"
                + " (id, job_type, status, created_at, heartbeat_at,"
                + "  processed_items, successful_items, failed_items)"
                + " values (gen_random_uuid(), '" + jobType + "', '" + status + "',"
                + "         now(), now(), 0, 0, 0)";
    }

}
