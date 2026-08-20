package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheAvailability;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheProperties;
import com.alejandro.mtoconfiguration.configuration.cache.ResilientCacheErrorHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.Cache;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El handler no debe tragarse todo por igual: un fallo de infraestructura se
 * degrada, uno de serialización se purga, y cualquier otro se propaga porque es
 * un bug nuestro y esconderlo lo vuelve mucho más caro de encontrar.
 */
class ResilientCacheErrorHandlerTest {

    private RedisCacheAvailability availability;
    private MeterRegistry meterRegistry;
    private ResilientCacheErrorHandler handler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        RedisCacheProperties properties = new RedisCacheProperties();
        properties.setDegradedRetryWindow(Duration.ofSeconds(30));
        availability = new RedisCacheAvailability(properties);
        meterRegistry = new SimpleMeterRegistry();
        handler = new ResilientCacheErrorHandler(availability, meterRegistry);

        cache = Mockito.mock(Cache.class);
        when(cache.getName()).thenReturn("normal:item");
    }

    @Test
    void shouldDegradeAndTripBreakerOnConnectionFailure() {
        RuntimeException failure = new RedisConnectionFailureException("Redis caido");

        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "k1"))
                .doesNotThrowAnyException();

        assertThat(availability.isAvailable()).isFalse();
        assertThat(errorCount("get", "infrastructure")).isEqualTo(1.0);
    }

    @Test
    void shouldDegradeOnTimeoutWrappedInAnotherException() {
        RuntimeException failure = new IllegalStateException("envoltorio",
                new QueryTimeoutException("Redis lento"));

        assertThatCode(() -> handler.handleCachePutError(failure, cache, "k1", "v1"))
                .doesNotThrowAnyException();

        assertThat(availability.isAvailable()).isFalse();
        assertThat(errorCount("put", "infrastructure")).isEqualTo(1.0);
    }

    @Test
    void shouldEvictPoisonedEntryWithoutTrippingBreakerOnSerializationFailure() {
        RuntimeException failure = new SerializationException("no se puede leer la entrada");

        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "k1"))
                .doesNotThrowAnyException();

        // Se purga la clave para que no envenene el endpoint hasta que expire el TTL...
        verify(cache, times(1)).evict("k1");
        // ...pero no es una caida de Redis, asi que la cache sigue disponible.
        assertThat(availability.isAvailable()).isTrue();
        assertThat(errorCount("get", "serialization")).isEqualTo(1.0);
    }

    @Test
    void shouldRethrowProgrammingErrorsInsteadOfHidingThem() {
        RuntimeException bug = new IllegalArgumentException("SpEL mal escrito en la clave");

        assertThatThrownBy(() -> handler.handleCacheGetError(bug, cache, "k1"))
                .isSameAs(bug);

        assertThat(availability.isAvailable()).isTrue();
        verify(cache, never()).evict(Mockito.any());
        assertThat(errorCount("get", "unexpected")).isEqualTo(1.0);
    }

    @Test
    void shouldRecoverWhenRedisAnswersAgain() {
        handler.handleCacheGetError(new RedisConnectionFailureException("caido"), cache, "k1");
        assertThat(availability.isAvailable()).isFalse();

        availability.markHealthy();

        assertThat(availability.isAvailable()).isTrue();
    }

    private double errorCount(String operation, String type) {
        return meterRegistry.counter("cache.errors",
                "cache", "normal:item",
                "operation", operation,
                "type", type).count();
    }
}
