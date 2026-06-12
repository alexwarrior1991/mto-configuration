package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisCacheConfig {

    @Bean("redisCacheKeyObjectMapper")
    public ObjectMapper redisCacheKeyObjectMapper() {
        return JsonMapper.builder()
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
            RedisCacheProperties redisCacheProperties
    ) {
        return builder -> builder
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

    private PolymorphicTypeValidator buildTypeValidator(RedisCacheProperties redisCacheProperties) {

        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder();

        redisCacheProperties.getAllowedSubtypes()
                .forEach(builder::allowIfSubType);
        
        return builder.build();
    }

}
