package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheErrorConfig;
import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheAvailability;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
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

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.timeout", () -> "500ms");
        registry.add("cache.redis.degraded-retry-window", () -> "30s");
        registry.add("cache.redis.allowed-subtypes[0]", () -> "java.util");
        registry.add("cache.redis.allowed-subtypes[1]", () -> "org.springframework.data.domain");
        registry.add("cache.redis.allowed-subtypes[2]", () -> "com.alejandro.mtoconfiguration");
    }

    @Test
    void shouldKeepServingFromDatabaseWhenRedisGoesDown() {
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
