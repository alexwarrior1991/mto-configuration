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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
// Sin disabledWithoutDocker a proposito: Testcontainers no distingue "no hay Docker"
// de "hay Docker pero no puedo hablar con el", y con ese flag un cliente mal
// configurado se saltaba los tests devolviendo verde. Aqui debe fallar en voz alta.
@Testcontainers
@Import(AbstractCriteriaSearchIT.SearchTestConfiguration.class)
public abstract class AbstractCriteriaSearchIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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
