package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheErrorConfig;
import com.alejandro.mtoconfiguration.configuration.cache.CacheGeneration;
import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheAvailability;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheEvictService;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La carrera de una cache que se rellena al leer, contra Redis de verdad y con la cadena de la
 * aplicacion: {@code ResilientCache} sobre el {@code RedisCacheManager} y el vaciado de
 * {@code RedisCacheEvictService}.
 * <p>
 * Una lectura que falla en la cache lee la base, y solo despues guarda lo leido. Si en medio una
 * escritura confirma y vacia la cache ({@code AFTER_COMMIT}), el {@code put} de la lectura llega
 * despues del {@code DEL} y deja en Redis lo de antes de la escritura, hasta el TTL: 24 horas un
 * catalogo. Paso en el e2e de mto-frontend: la lista que la pantalla pidio al abrirse, lenta con la
 * plataforma recien levantada, tapo el alta que se hizo mientras tanto.
 * <p>
 * Cada test pausa la lectura entre la base y el {@code put} con dos cerrojos, escribe y vacia en
 * esa pausa, y la deja terminar: el orden que en produccion es mala suerte, aqui es seguro.
 */
@Testcontainers(disabledWithoutDocker = true)
@ImportAutoConfiguration({DataRedisAutoConfiguration.class, CacheAutoConfiguration.class})
@SpringBootTest(classes = {
        RedisCacheConfig.class,
        RedisCacheKeyGenerator.class,
        RedisCacheAvailability.class,
        CacheErrorConfig.class,
        CacheGeneration.class,
        RedisCacheEvictService.class,
        CacheGenerationIT.RaceTestConfiguration.class
})
class CacheGenerationIT {

    /** El catalogo de pega: {@code RaceLovService}, cuyas claves vacia {@code evictAfterLovWrite("RaceLov")}. */
    private static final String LOV = "RaceLov";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RaceLovService service;

    @Autowired
    private RedisCacheEvictService evictService;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        // Como en RedisCacheResilienceIT: un timeout que se pasara armaria el cortocircuito y la
        // cache dejaria de guardar, que aqui pareceria el arreglo funcionando.
        registry.add("spring.data.redis.timeout", () -> "5s");
        registry.add("cache.redis.allowed-subtypes[0]", () -> "java.util");
        registry.add("cache.redis.allowed-subtypes[1]", () -> "com.alejandro.mtoconfiguration");
    }

    @BeforeEach
    void empezarDeCero() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        service.reset(List.of("A", "B", "C"));
    }

    @Test
    @DisplayName("Una lectura que guarda despues de que una escritura vacie la cache no deja la lista vieja")
    void unaLecturaQueGuardaTrasElVaciadoNoDejaLaListaVieja() throws Exception {
        Pausa pausa = new Pausa();
        CompletableFuture<List<String>> lenta = lecturaPausada(pausa);

        // La escritura confirma mientras la lectura esta entre la base y el put, y vacia la cache
        // al confirmar, como LovCacheEvictionListener.
        service.insertar("D");
        evictService.evictAfterLovWrite(LOV);

        pausa.reanudar.countDown();
        assertThat(lenta.get(10, TimeUnit.SECONDS))
                .as("la lectura lenta devuelve lo que leyo antes de la escritura")
                .containsExactly("A", "B", "C");

        assertThat(service.findAll())
                .as("la lectura siguiente tiene que ver el alta, no la lista que guardo la lenta")
                .containsExactly("A", "B", "C", "D");
    }

    @Test
    @DisplayName("Sin escrituras de por medio, la segunda lectura sigue saliendo de Redis")
    void sinEscriturasLaSegundaLecturaSaleDeRedis() {
        assertThat(service.findAll()).containsExactly("A", "B", "C");
        assertThat(service.findAll()).containsExactly("A", "B", "C");

        assertThat(service.loads()).as("la segunda lectura no debia ir a la base").isEqualTo(1);
    }

    @Test
    @DisplayName("Tras una lectura deshecha por una escritura, la siguiente vuelve a guardarse")
    void trasUnaLecturaDeshechaLaSiguienteVuelveAGuardarse() throws Exception {
        Pausa pausa = new Pausa();
        CompletableFuture<List<String>> lenta = lecturaPausada(pausa);
        service.insertar("D");
        evictService.evictAfterLovWrite(LOV);
        pausa.reanudar.countDown();
        lenta.get(10, TimeUnit.SECONDS);

        assertThat(service.findAll()).containsExactly("A", "B", "C", "D");
        assertThat(service.findAll()).containsExactly("A", "B", "C", "D");

        assertThat(service.loads())
                .as("la lenta, la que vio el alta y ninguna mas: la tercera salio de Redis")
                .isEqualTo(2);
    }

    /**
     * Lanza una lectura por el proxy en un hilo virtual y vuelve cuando ya ha leido la base y
     * espera, antes del put, a que se abra {@code pausa.reanudar}.
     */
    private CompletableFuture<List<String>> lecturaPausada(Pausa pausa) {
        service.pausarLaSiguienteLectura(pausa);
        CompletableFuture<List<String>> lectura =
                CompletableFuture.supplyAsync(service::findAll, tarea -> Thread.ofVirtual().start(tarea));
        esperar(pausa.leida);
        return lectura;
    }

    private static void esperar(CountDownLatch cerrojo) {
        try {
            if (!cerrojo.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("el cerrojo no se abrio en 10 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Los dos cerrojos de una lectura pausada: ya ha leido la base, y puede seguir hasta el put. */
    static final class Pausa {
        final CountDownLatch leida = new CountDownLatch(1);
        final CountDownLatch reanudar = new CountDownLatch(1);
    }

    @Configuration
    static class RaceTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        RaceLovService raceLovService() {
            return new RaceLovService();
        }
    }

    /**
     * Un catalogo con la cache de {@code AbstractLovCrudService.findAll}: misma cache, mismo generador
     * de claves y mismo {@code unless}. La «base» es una lista en memoria.
     */
    static class RaceLovService {

        private volatile List<String> rows = new ArrayList<>();
        private final AtomicInteger loads = new AtomicInteger();
        private volatile Pausa pausa;

        @Cacheable(
                cacheNames = CacheNames.LOV_LIST,
                keyGenerator = "redisCacheKeyGenerator",
                unless = "#result == null || #result.isEmpty()"
        )
        public List<String> findAll() {
            List<String> snapshot = new ArrayList<>(rows);
            loads.incrementAndGet();

            Pausa enCurso = pausa;
            if (enCurso != null) {
                pausa = null;
                enCurso.leida.countDown();
                esperar(enCurso.reanudar);
            }
            return snapshot;
        }

        void reset(List<String> initial) {
            rows = new ArrayList<>(initial);
            loads.set(0);
            pausa = null;
        }

        void insertar(String code) {
            List<String> next = new ArrayList<>(rows);
            next.add(code);
            rows = next;
        }

        void pausarLaSiguienteLectura(Pausa siguiente) {
            pausa = siguiente;
        }

        int loads() {
            return loads.get();
        }
    }
}
