package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheErrorConfig;
import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheAvailability;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La caché es una optimización: si Redis desaparece, la aplicación tiene que
 * seguir sirviendo desde base de datos, más lenta pero viva.
 * <p>
 * Contenedor propio y no compartido con RedisCacheIT: este test lo para a
 * mitad de ejecución a propósito.
 */
@Testcontainers(disabledWithoutDocker = true)
// Con @SpringBootTest(classes = ...) Spring Boot NO aplica sus autoconfiguraciones,
// asi que nadie crearia el RedisConnectionFactory ni el RedisCacheManager y
// @EnableCaching fallaria con "No qualifying bean of type CacheManager".
// Se importan solo las dos que hacen falta, en vez de arrancar la aplicacion entera.
// El orden importa y no es cosmetico: los tres tests comparten el mismo contenedor
// estatico, y dos de ellos lo PARAN a proposito. El que necesita Redis vivo tiene que
// ejecutarse primero; sin orden fijo, JUnit puede arrancar por uno que lo tumba y el
// primero falla al no encontrar la cache que esperaba.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ImportAutoConfiguration({DataRedisAutoConfiguration.class, CacheAutoConfiguration.class})
@SpringBootTest(classes = {
        RedisCacheConfig.class,
        RedisCacheKeyGenerator.class,
        RedisCacheAvailability.class,
        CacheErrorConfig.class,
        RedisCacheResilienceIT.ResilienceTestConfiguration.class
})
class RedisCacheResilienceIT {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private FragileTestService service;

    @Autowired
    private FragileTestRepository repository;

    @Autowired
    private RedisCacheAvailability availability;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        // 2s y no 500ms: con el contenedor recien arrancado, la escritura que puebla la cache en
        // shouldKeepServingFromDatabaseWhenRedisGoesDown se pasaba a veces de ese margen. Cuando
        // ocurre, ResilientCacheErrorHandler se traga el fallo —que es su cometido— y la segunda
        // llamada vuelve al repositorio, de modo que el test fallaba por una carrera con el
        // arranque y no por lo que pretende comprobar.
        //
        // No encarece la parte de degradacion: en cuanto Redis cae, la disponibilidad se marca
        // como perdida y las llamadas siguientes cortocircuitan sin intentar conectarse, asi que
        // el timeout se paga una vez por test, no en cada operacion.
        registry.add("spring.data.redis.timeout", () -> "2s");
        registry.add("cache.redis.degraded-retry-window", () -> "30s");
        registry.add("cache.redis.allowed-subtypes[0]", () -> "java.util");
        registry.add("cache.redis.allowed-subtypes[1]", () -> "org.springframework.data.domain");
        registry.add("cache.redis.allowed-subtypes[2]", () -> "com.alejandro.mtoconfiguration");
    }

    /**
     * Abre y cierra una conexión antes de tocar la caché.
     * <p>
     * No es precaución de más: con el contenedor recién arrancado, la primera operación contra
     * Redis paga la conexión en frío de Lettuce, y si se pasa de {@code spring.data.redis.timeout}
     * el {@code ResilientCacheErrorHandler} se la traga —su cometido— pero además
     * {@link RedisCacheAvailability#markDegraded} arma el cortocircuito durante
     * {@code cache.redis.degraded-retry-window}, aquí 30 segundos. A partir de ahí
     * {@code ResilientCache} corta ANTES de llamar a Redis, así que la segunda llamada del test
     * vuelve al repositorio y la aserción falla con "was 2 times" por una carrera de arranque y no
     * por lo que el test pretende comprobar.
     * <p>
     * Pagando ese coste aquí, fuera del camino de la caché, una conexión lenta no puede armar el
     * cortocircuito. Subir el timeout —que es lo que se intentó antes— solo baja la probabilidad.
     */
    private void calentarLaConexion() {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    try (RedisConnection connection = connectionFactory.getConnection()) {
                        assertThat(connection.ping()).isEqualTo("PONG");
                    }
                });
    }

    @Test
    @Order(1)
    void shouldKeepServingFromDatabaseWhenRedisGoesDown() {
        calentarLaConexion();

        assertThat(availability.isAvailable())
                .withFailMessage("El cortocircuito ya estaba armado antes de empezar: alguna "
                        + "operación falló durante el calentamiento y la caché se considera "
                        + "degradada durante la ventana de reintento, de modo que este test no "
                        + "estaría comprobando lo que cree.")
                .isTrue();

        when(repository.load("K")).thenReturn(new TestValue("K", "desde-bd"));

        // Con Redis vivo: primera llamada puebla la caché, la segunda es un acierto.
        assertThat(service.item("K").name()).isEqualTo("desde-bd");
        assertThat(service.item("K").name()).isEqualTo("desde-bd");
        verify(repository, times(1)).load("K");

        redis.stop();
        Mockito.clearInvocations(repository);

        // Sin Redis: no debe lanzar, y el dato tiene que venir del repositorio.
        TestValue afterOutage = service.item("K");

        assertThat(afterOutage.name()).isEqualTo("desde-bd");
        verify(repository, atLeastOnce()).load("K");
        assertThat(availability.isAvailable()).isFalse();
    }

    @Test
    @Order(2)
    void shouldCountCacheErrorsSoTheDegradationIsVisible() {
        when(repository.load("M")).thenReturn(new TestValue("M", "desde-bd"));

        if (redis.isRunning()) {
            redis.stop();
        }

        service.item("M");

        double errors = meterRegistry.find("cache.errors").counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();

        assertThat(errors).isGreaterThan(0.0);
    }

    @Test
    @Order(3)
    void shouldShortCircuitInsteadOfRetryingRedisOnEveryCall() {
        when(repository.load("S")).thenReturn(new TestValue("S", "desde-bd"));

        if (redis.isRunning()) {
            redis.stop();
        }

        service.item("S");
        assertThat(availability.isAvailable()).isFalse();

        double errorsAfterFirstCall = totalCacheErrors();

        // La segunda llamada debe cortocircuitar: ni intenta Redis, ni suma errores.
        service.item("S");

        assertThat(totalCacheErrors()).isEqualTo(errorsAfterFirstCall);
    }

    private double totalCacheErrors() {
        return meterRegistry.find("cache.errors").counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();
    }

    @Configuration
    static class ResilienceTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        FragileTestRepository fragileTestRepository() {
            return Mockito.mock(FragileTestRepository.class);
        }

        @Bean
        FragileTestService fragileTestService(FragileTestRepository repository) {
            return new FragileTestService(repository);
        }
    }

    interface FragileTestRepository {
        TestValue load(String key);
    }

    static class FragileTestService {

        private final FragileTestRepository repository;

        FragileTestService(FragileTestRepository repository) {
            this.repository = repository;
        }

        @Cacheable(cacheNames = CacheNames.NORMAL_ITEM, keyGenerator = "redisCacheKeyGenerator")
        public TestValue item(String key) {
            return repository.load(key);
        }
    }

    record TestValue(String code, String name) {
    }
}
