package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.mapper.commons.ReferenceMapper;
import com.alejandro.mtoconfiguration.mapper.infraestructure.CantileverMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.DisconnectorMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ExecutionPackageMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ProfileMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SectionInsulatorMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.StationMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SteadyArmMapperImpl;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackMapperImpl;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Optional;

/**
 * Base de los tests de reconciliacion de hijos contra PostgreSQL real.
 *
 * <p>Los tests unitarios de los mappers demuestran que el objeto queda bien <b>en memoria</b>:
 * la instancia existente se reutiliza, no aparecen copias y los hijos apuntan a su padre. Lo que
 * no pueden demostrar es lo unico que le importa al usuario: el SQL que Hibernate emite al hacer
 * flush. Que el UPDATE caiga en la fila correcta, que {@code orphanRemoval} borre exactamente la
 * que se quito, que no se cuele ningun INSERT de mas y que el {@code @OrderColumn} se recalcule al
 * sacar un hijo del medio son cosas que solo se ven contra una base de datos.
 *
 * <p>Por eso cada test sigue siempre el mismo guion: persistir el estado inicial, vaciar el
 * contexto de persistencia, <b>releer</b> el padre como haria el servicio en una peticion nueva,
 * aplicar el mapeo, volver a vaciar y comprobar el resultado leyendo de la base de datos. Sin los
 * {@code flushAndClear} el test estaria comprobando el grafo que quedo en memoria, es decir lo
 * mismo que ya comprueba el unitario.
 *
 * <p>{@code MasterDataService} va simulado: resuelve listas de valores contra dieciseis
 * repositorios y no interviene en la reconciliacion, que es lo que aqui se prueba.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AbstractChildMergeIT.MergeTestConfiguration.class,
        ReferenceMapper.class,
        ProfileMapperImpl.class,
        CantileverMapperImpl.class,
        SteadyArmMapperImpl.class,
        DisconnectorMapperImpl.class,
        SectionInsulatorMapperImpl.class,
        TrackMapperImpl.class,
        StationMapperImpl.class,
        ExecutionPackageMapperImpl.class
})
public abstract class AbstractChildMergeIT {

    /**
     * Contenedor unico para todas las clases de test, arrancado una sola vez y nunca parado. Es el
     * mismo patron y por el mismo motivo que en {@code AbstractCriteriaSearchIT}: Spring reutiliza
     * el contexto entre clases y ese contexto resolvio @DynamicPropertySource una unica vez, asi
     * que un contenedor por clase dejaria el DataSource apuntando a un puerto ya cerrado.
     */
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @PersistenceContext
    protected EntityManager em;

    @MockitoBean
    protected MasterDataService masterDataService;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
    }

    /**
     * Escribe lo pendiente y vacia el contexto, de modo que la siguiente lectura vuelva a la base
     * de datos en lugar de servir el objeto que ya estaba en memoria.
     */
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Numero de filas de una tabla, leido directamente en SQL para no pasar por el contexto. */
    protected long contarFilas(String tabla) {
        return ((Number) em.createNativeQuery("select count(*) from " + tabla).getSingleResult()).longValue();
    }

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
    static class MergeTestConfiguration {

        /**
         * BaseEntity exige createUser y versionUser no nulos, y @DataJpaTest no carga la
         * configuracion de seguridad que los rellena en produccion.
         */
        @Bean
        AuditorAware<String> springSecurityAuditorAware() {
            return () -> Optional.of("test");
        }
    }
}
