package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Cada entidad publicable tiene DOS repositorios para el mismo tipo de dominio (el suyo
 * y su {@code *CriteriaSearchRepository}), y {@code Repositories} indexa por tipo de
 * dominio: con dos candidatos gana el ultimo registrado, que depende del orden de
 * escaneo del classpath. Si sale el de busqueda, el listener no reconoce un
 * {@code MessagingEntityGraphRepository}, se salta el {@code @EntityGraph} y publica la
 * entidad con las relaciones sin inicializar.
 * <p>
 * Aqui se fija que la eleccion la decide la capacidad del repositorio y no el orden en
 * que este registrado. La resolucion contra el contexto real de Spring Data la cubre
 * {@code MasterDataRepositoryResolverIT}.
 */
class MasterDataRepositoryResolverTest {

    private final GenericApplicationContext context = new GenericApplicationContext();

    private interface CantileverMessagingRepository
            extends JpaRepository<Cantilever, Long>, MessagingEntityGraphRepository<Cantilever> {
    }

    private interface CantileverSearchRepository extends JpaRepository<Cantilever, Long> {
    }

    /** Un proxy de Hibernate llega como subclase del tipo de dominio, no como el tipo exacto. */
    private static class CantileverProxy extends Cantilever {
    }

    @AfterEach
    void cerrarContexto() {
        context.close();
    }

    private MasterDataRepositoryResolver resolverCon(Object... repositorios) {
        context.refresh();

        for (int i = 0; i < repositorios.length; i++) {
            context.getBeanFactory().registerSingleton("repositorio" + i, repositorios[i]);
        }

        return new MasterDataRepositoryResolver(context);
    }

    @Test
    void entreVariosRepositoriosDelMismoTipoGanaElDeMensajeria() {
        CantileverSearchRepository busqueda = mock(CantileverSearchRepository.class);
        CantileverMessagingRepository mensajeria = mock(CantileverMessagingRepository.class);

        // El de busqueda se registra primero a proposito: si la eleccion dependiese del
        // orden, ganaria el.
        MasterDataRepositoryResolver resolver = resolverCon(busqueda, mensajeria);

        assertThat(resolver.resolve(new Cantilever())).get().isSameAs(mensajeria);
    }

    @Test
    void unaEntidadQueLlegaComoProxyDeHibernateResuelveIgualSuRepositorio() {
        CantileverMessagingRepository mensajeria = mock(CantileverMessagingRepository.class);

        MasterDataRepositoryResolver resolver = resolverCon(mensajeria);

        assertThat(resolver.resolve(new CantileverProxy())).get().isSameAs(mensajeria);
    }

    @Test
    void elRepositorioDeMensajeriaDeOtraEntidadNoSirveParaEsta() {
        CantileverMessagingRepository mensajeria = mock(CantileverMessagingRepository.class);

        MasterDataRepositoryResolver resolver = resolverCon(mensajeria);

        assertThat(resolver.resolve(new Station())).isEmpty();
    }

    @Test
    void sinNingunRepositorioNoSeResuelveNada() {
        MasterDataRepositoryResolver resolver = resolverCon();

        assertThat(resolver.resolve(new Cantilever())).isEmpty();
    }

    @Test
    void unaEntidadNulaNoResuelveNingunRepositorio() {
        MasterDataRepositoryResolver resolver = resolverCon();

        assertThat(resolver.resolve(null)).isEmpty();
    }
}
