package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisCacheConfig {

    @Bean("redisCacheKeyObjectMapper")
    public ObjectMapper redisCacheKeyObjectMapper() {
        // Este mapper sólo genera claves de caché, nunca valores. El orden debe ser
        // determinista: SearchRequestDTO.filters llega como LinkedHashMap y conserva el
        // orden del JSON del cliente, de modo que la misma búsqueda escrita con los
        // campos en otro orden produciría una clave distinta y una entrada duplicada.
        return JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.FAIL_ON_ORDER_MAP_BY_INCOMPARABLE_KEY)
                .build();
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            RedisCacheProperties redisCacheProperties,
            @Value("${cache.application:mto-configuration}") String applicationName
    ) {
        PolymorphicTypeValidator typeValidator = buildTypeValidator(redisCacheProperties);

        GenericJacksonJsonRedisSerializer serializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(typeValidator)
                        .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(redisCacheProperties.getDefaultTtl())
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                )
                .computePrefixWith(cacheName -> applicationName + "::" + cacheName + "::");
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            RedisCacheConfiguration defaultConfiguration,
            RedisCacheProperties redisCacheProperties,
            RedisConnectionFactory redisConnectionFactory
    ) {
        return builder -> builder
                .cacheWriter(immediateCacheWriter(redisConnectionFactory))
                .withCacheConfiguration(
                        CacheNames.NORMAL_ITEM,
                        defaultConfiguration.entryTtl(redisCacheProperties.getNormalItemTtl())
                )
                .withCacheConfiguration(
                        CacheNames.NORMAL_LIST,
                        defaultConfiguration.entryTtl(redisCacheProperties.getNormalListTtl())
                )
                .withCacheConfiguration(
                        CacheNames.NORMAL_PAGE,
                        defaultConfiguration.entryTtl(redisCacheProperties.getNormalPageTtl())
                )
                .withCacheConfiguration(
                        CacheNames.NORMAL_SEARCH,
                        defaultConfiguration.entryTtl(redisCacheProperties.getNormalSearchTtl())
                )
                .withCacheConfiguration(
                        CacheNames.LOV_ITEM,
                        defaultConfiguration.entryTtl(redisCacheProperties.getLovItemTtl())
                )
                .withCacheConfiguration(
                        CacheNames.LOV_LIST,
                        defaultConfiguration.entryTtl(redisCacheProperties.getLovListTtl())
                );

    }

    /**
     * El escritor de la caché, con escrituras inmediatas: el {@code put} vuelve cuando Redis ya
     * tiene el valor.
     * <p>
     * Spring Data Redis 4 escribe en segundo plano por defecto cuando la factoría de conexiones es
     * también reactiva, y la de Lettuce lo es. El {@code SET} salía por la conexión reactiva y el
     * {@code put} no lo esperaba. Así pasaban tres cosas:
     * <ul>
     *   <li>una lectura justo después podía llegar a Redis antes que el valor y volver a la base de
     *       datos, sin excepción ni traza (de ahí los aciertos intermitentes de
     *       {@code RedisCacheIT} y {@code RedisCacheResilienceIT});</li>
     *   <li>{@link ResilientCache} daba la caché por sana antes de saber si la escritura había
     *       llegado;</li>
     *   <li>y un {@code SET} fallido no pasaba por {@link ResilientCacheErrorHandler}: ni contaba en
     *       {@code cache.errors} ni armaba el cortocircuito.</li>
     * </ul>
     * Por lo demás es el escritor que monta Spring Boot: sin bloqueo y con {@code KEYS} para vaciar.
     * Las estadísticas ({@code spring.cache.redis.enable-statistics}) se le aplican al construir el
     * {@code RedisCacheManager}. Lo fija {@code RedisCacheConfigTest}.
     */
    private static RedisCacheWriter immediateCacheWriter(RedisConnectionFactory connectionFactory) {
        return RedisCacheWriter.create(connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites);
    }

    private PolymorphicTypeValidator buildTypeValidator(RedisCacheProperties redisCacheProperties) {

        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder();

        redisCacheProperties.getAllowedSubtypes()
                .forEach(builder::allowIfSubType);
        
        return builder.build();
    }

}
