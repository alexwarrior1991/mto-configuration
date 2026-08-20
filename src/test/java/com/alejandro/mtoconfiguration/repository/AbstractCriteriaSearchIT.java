package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.model.commons.PageableDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base de los tests de búsqueda por criteria.
 * <p>
 * PostgreSQL real y no H2: el proyecto usa dialecto, secuencias y tipos de
 * PostgreSQL, y un LIKE sobre UPPER(...) no se comporta igual en todos los motores.
 * <p>
 * El esquema lo genera Hibernate desde las entidades porque el repositorio no
 * tiene migraciones Flyway (`db/migration` no existe), así que Flyway se desactiva.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AbstractCriteriaSearchIT.SearchTestConfiguration.class)
public abstract class AbstractCriteriaSearchIT {

    /**
     * Contenedor unico para TODAS las clases de test, arrancado una sola vez y nunca
     * parado (Ryuk lo elimina al terminar la JVM). Es el patron "singleton container"
     * y aqui no es una optimizacion, es obligatorio.
     * <p>
     * Con @Testcontainers + @Container, Testcontainers arranca y para el contenedor
     * una vez POR CLASE de test, con un puerto aleatorio distinto cada vez. Pero
     * Spring cachea y reutiliza el mismo contexto entre clases, y ese contexto
     * resolvio @DynamicPropertySource una unica vez: su DataSource sigue apuntando al
     * puerto del PRIMER contenedor, que ya esta parado. De ahi el
     * "Connection to localhost:xxxxx refused" en cuanto se ejecutan varias clases
     * seguidas, y de ahi tambien que tardaran tanto (un PostgreSQL nuevo por clase).
     * <p>
     * Sin @Testcontainers no hay gestion de ciclo de vida: si Docker no responde,
     * este start() falla al cargar la clase y los tests fallan en voz alta, que es lo
     * que queremos. Nada de saltarselos en silencio.
     */
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @PersistenceContext
    protected EntityManager em;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
    }

    /** Petición de búsqueda con orden explícito. */
    protected SearchRequestDTO search(Map<String, Object> filters, String sortBy, String direction) {
        PageableDTO pageable = new PageableDTO();
        pageable.setPage(0);
        pageable.setSize(50);
        pageable.setSortBy(List.of(sortBy));
        pageable.setSortDirection(List.of(direction));

        SearchRequestDTO request = new SearchRequestDTO();
        request.setFilters(filters);
        request.setPageable(pageable);
        return request;
    }

    /** Petición de búsqueda con el orden por defecto del repositorio. */
    protected SearchRequestDTO search(Map<String, Object> filters) {
        PageableDTO pageable = new PageableDTO();
        pageable.setPage(0);
        pageable.setSize(50);
        pageable.setSortBy(List.of());
        pageable.setSortDirection(List.of());

        SearchRequestDTO request = new SearchRequestDTO();
        request.setFilters(filters);
        request.setPageable(pageable);
        return request;
    }

    protected void flushAndClear() {
        em.flush();
        em.clear();
    }

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
    static class SearchTestConfiguration {

        /**
         * BaseEntity exige createUser y versionUser no nulos, y @DataJpaTest no
         * carga la configuración de seguridad que los rellena en producción.
         */
        @Bean
        AuditorAware<String> springSecurityAuditorAware() {
            return () -> Optional.of("test");
        }
    }
}
