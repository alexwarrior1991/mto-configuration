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

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestDatabase::url);
        registry.add("spring.datasource.username", PostgresTestDatabase::username);
        registry.add("spring.datasource.password", PostgresTestDatabase::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // El repositorio no tiene migraciones Flyway: el esquema lo genera Hibernate
        // desde las entidades, que ademas es lo que valida el mapeo de outbox_message.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
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
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        return container;
    }
}
