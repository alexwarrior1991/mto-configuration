package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import com.alejandro.mtoconfiguration.support.SqlStatementRecorder;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Los casos de {@link AbstractDeleteWithDependentsIT} (los 409, lo que se va con su padre, la via
 * entera, la carrera con un alta), con las tablas <b>fuera de {@code public}</b> y sin su esquema
 * en el {@code search_path}, que es como corre el servicio en la plataforma.
 *
 * <p>El perfil {@code docker} pone las tablas en el esquema {@code mto_configuration}
 * ({@code spring.flyway.default-schema} y {@code hibernate.default_schema}). Hibernate antepone ese
 * esquema al SQL que genera, pero no al SQL nativo que se escribe a mano: ese tiene que llevar
 * {@code {h-schema}} delante de cada tabla. {@code DeletionGuard} y {@code LiveParentGuard}
 * nacieron sin el, y como todos los IT corren en {@code public} nadie lo vio hasta la imagen
 * publicada: {@code relation "execution_package" does not exist}, y un 500 en cada alta de
 * estacion o de via y en cada borrado.
 *
 * <p>Aqui Flyway migra a un esquema propio de la misma base, y la conexion de la aplicacion no lo
 * tiene en su {@code search_path}: una tabla nombrada sin esquema o no existe, o es la de
 * {@code public} que dejaron otros IT, vacia de lo de este; en los dos casos estos tests fallan.
 * El SQL a mano del propio test va por otra conexion, que si lo ve ({@code currentSchema}).
 *
 * <p>Un SQL nativo nuevo que nombre una tabla lleva su {@code {h-schema}}; si lo ejercita un alta,
 * un borrado o la cascada de una via, esta clase lo vigila.
 */
@SpringBootTest
@DisplayName("Borrar con cosas colgando, con las tablas en un esquema que no es public")
class DeleteWithDependentsInOwnSchemaIT extends AbstractDeleteWithDependentsIT {

    private static final String SCHEMA = "mto_configuration_it";

    private static JdbcTemplate inTheSchema;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
        registry.add("spring.jpa.properties.hibernate.session_factory.statement_inspector",
                SqlStatementRecorder.class::getName);
        // Lo mismo que cambia application-docker.yaml; va despues, y gana.
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @Override
    protected synchronized JdbcTemplate fixtures(JdbcTemplate ofTheContext) {
        if (inTheSchema == null) {
            String url = PostgresTestDatabase.url();
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                    url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA,
                    PostgresTestDatabase.username(), PostgresTestDatabase.password(), true);
            inTheSchema = new JdbcTemplate(dataSource);
        }

        return inTheSchema;
    }
}
