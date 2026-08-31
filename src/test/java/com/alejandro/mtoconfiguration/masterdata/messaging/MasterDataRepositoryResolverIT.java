package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code MasterDataRepositoryResolver} depende de {@code Repositories}, que necesita
 * un {@code ApplicationContext} real con los repositorios Spring Data ya cableados:
 * un mock de contexto no reproduce esa resolucion. Se ejercita aqui, contra la misma
 * base de datos migrada que usa {@code MessagingEntityGraphIT}, porque es exactamente
 * el mismo repositorio el que despues usa {@code MasterDataEntityChangedEventListener}
 * para decidir si puede cargar el grafo de mensajeria.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MasterDataRepositoryResolverIT {

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
    }

    private static class EntidadSinRepositorio extends BaseEntity {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    private MasterDataRepositoryResolver resolver() {
        // No es un bean bajo @DataJpaTest (el slice no escanea @Component), pero su
        // constructor solo necesita el ApplicationContext, asi que se instancia igual
        // que lo haria Spring en el arranque completo.
        return new MasterDataRepositoryResolver(applicationContext);
    }

    @Test
    void unaEntidadNulaNoResuelveNingunRepositorio() {
        assertThat(resolver().resolve(null)).isEmpty();
    }

    @Test
    void unaEntidadGestionadaPorSpringDataResuelveSuRepositorioJpa() {
        Optional<JpaRepository<IEntity, Long>> repository = resolver().resolve(new Cantilever());

        assertThat(repository).isPresent();
    }

    @Test
    void cantileverTieneVariosRepositoriosParaElMismoTipoDeDominio() {
        // Es la razon de ser del test siguiente: Repositories indexa por tipo de dominio,
        // asi que con dos candidatos gana el ultimo registrado y cual sea eso depende del
        // orden de escaneo del classpath.
        assertThat(applicationContext.getBeanNamesForType(JpaRepository.class))
                .contains("cantileverRepository", "cantileverCriteriaSearchRepository");
    }

    @Test
    void elRepositorioResueltoParaCantileverEsElMismoQueUsaElListenerParaCargarElGrafoDeMensajeria() {
        // Es exactamente esta comprobacion (instanceof MessagingEntityGraphRepository)
        // la que hace MasterDataEntityChangedEventListener con lo que este resolver le
        // devuelve: si sale el repositorio de busqueda, el listener se salta el
        // @EntityGraph y publica la entidad con las relaciones sin inicializar.
        Optional<JpaRepository<IEntity, Long>> repository = resolver().resolve(new Cantilever());

        assertThat(repository).get().isInstanceOf(MessagingEntityGraphRepository.class);
    }

    @Test
    void unaEntidadSinRepositorioAsociadoNoResuelveNada() {
        assertThat(resolver().resolve(new EntidadSinRepositorio())).isEmpty();
    }
}
