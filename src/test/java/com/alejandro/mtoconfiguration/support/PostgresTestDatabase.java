package com.alejandro.mtoconfiguration.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL para los tests de integracion.
 * <p>
 * Por defecto levanta un contenedor unico para toda la JVM (patron singleton
 * container: con @Container por clase, Spring reutiliza el contexto pero el
 * DataSource sigue apuntando al puerto del primer contenedor, ya parado).
 * <p>
 * Si se indica {@code -Dmto.test.postgres.url=...} se usa esa base de datos en lugar
 * de arrancar nada. Sirve para ejecutar la suite en entornos sin Docker disponible
 * apuntando a un PostgreSQL ya existente; la base debe ser desechable, porque el
 * esquema se crea y se destruye en cada ejecucion.
 */
public final class PostgresTestDatabase {

    public static final String URL_PROPERTY = "mto.test.postgres.url";
    public static final String USERNAME_PROPERTY = "mto.test.postgres.username";
    public static final String PASSWORD_PROPERTY = "mto.test.postgres.password";

    private static final PostgreSQLContainer<?> CONTAINER = startContainerIfNeeded();

    private PostgresTestDatabase() {
    }

    /**
     * El esquema lo crean las MIGRACIONES, no Hibernate.
     * <p>
     * Con {@code ddl-auto: create-drop} los tests corren contra una aproximacion del
     * esquema real: Hibernate sabe de columnas y tipos, pero no de los valores por
     * defecto, los indices ni las restricciones que viven en los scripts. Un mensaje
     * del outbox necesita que la base le asigne su numero de secuencia, y eso solo
     * existe en la migracion. Corriendo contra el esquema migrado desaparece toda esa
     * clase de divergencia entre lo que se prueba y lo que se despliega.
     */
    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestDatabase::url);
        registry.add("spring.datasource.username", PostgresTestDatabase::username);
        registry.add("spring.datasource.password", PostgresTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // application.yaml da a Flyway su propia url/user/password, asi que no basta
        // con apuntar el datasource: sin esto migraria OTRA base de datos.
        registry.add("spring.flyway.url", PostgresTestDatabase::url);
        registry.add("spring.flyway.user", PostgresTestDatabase::username);
        registry.add("spring.flyway.password", PostgresTestDatabase::password);
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-version", () -> "1");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
    }

    public static String url() {
        return CONTAINER != null ? CONTAINER.getJdbcUrl() : System.getProperty(URL_PROPERTY);
    }

    public static String username() {
        return CONTAINER != null ? CONTAINER.getUsername() : System.getProperty(USERNAME_PROPERTY, "postgres");
    }

    public static String password() {
        return CONTAINER != null ? CONTAINER.getPassword() : System.getProperty(PASSWORD_PROPERTY, "postgres");
    }

    private static PostgreSQLContainer<?> startContainerIfNeeded() {
        String externalUrl = System.getProperty(URL_PROPERTY);

        if (externalUrl != null && !externalUrl.isBlank()) {
            return null;
        }

        // Sin gestion de ciclo de vida a proposito: si Docker no responde, el fallo se
        // ve al cargar la clase y los tests fallan en voz alta. Nada de saltarselos.
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:17-alpine");
        container.start();
        return container;
    }
}
