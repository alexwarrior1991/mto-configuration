package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.types.Expiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fija que la caché escribe en Redis antes de volver.
 * <p>
 * Spring Data Redis 4 escribe en segundo plano por defecto cuando la factoría de conexiones es
 * también reactiva, como la de Lettuce: el {@code SET} salía por la conexión reactiva y el
 * {@code put} no lo esperaba. Una lectura justo después podía no encontrar el valor, sin excepción
 * ni traza; de ahí los aciertos intermitentes de {@code RedisCacheIT} y
 * {@code RedisCacheResilienceIT}. Y un {@code SET} fallido no llegaba a
 * {@code ResilientCacheErrorHandler}.
 * <p>
 * Aquí no hace falta Redis: con una factoría de Lettuce simulada, que es síncrona y reactiva a la
 * vez, un {@code put} tiene que ir por la conexión síncrona y no pedir nunca la reactiva.
 */
class RedisCacheConfigTest {

    private LettuceConnectionFactory connectionFactory;
    private RedisStringCommands stringCommands;

    @BeforeEach
    void setUp() {
        connectionFactory = mock(LettuceConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        stringCommands = mock(RedisStringCommands.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(stringCommands);
    }

    @Test
    @DisplayName("Un put escribe por la conexión síncrona antes de volver y nunca pide la reactiva")
    void unPutEscribeAntesDeVolver() {
        Cache cache = cacheManager().getCache(CacheNames.NORMAL_ITEM);

        cache.put("clave", "valor");

        verify(stringCommands).set(any(byte[].class), any(byte[].class), any(Expiration.class), any(SetOption.class));
        verify(connectionFactory, never()).getReactiveConnection();
    }

    @Test
    @DisplayName("Cambiar el escritor no apaga las estadísticas de Spring Boot")
    void lasEstadisticasSiguenActivas() {
        RedisCache cache = (RedisCache) cacheManager().getCache(CacheNames.NORMAL_ITEM);

        cache.put("clave", "valor");

        assertThat(cache.getStatistics().getPuts()).isEqualTo(1);
    }

    /**
     * Lo que hace Spring Boot con {@code spring.cache.redis.enable-statistics}: el builder de la
     * factoría, las estadísticas y después los customizers, con la configuración real.
     */
    private RedisCacheManager cacheManager() {
        RedisCacheConfig config = new RedisCacheConfig();
        RedisCacheProperties properties = new RedisCacheProperties();
        RedisCacheConfiguration defaults = config.redisCacheConfiguration(properties, "mto-configuration");

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .enableStatistics();
        config.redisCacheManagerBuilderCustomizer(defaults, properties, connectionFactory).customize(builder);

        RedisCacheManager cacheManager = builder.build();
        cacheManager.afterPropertiesSet();
        return cacheManager;
    }
}
