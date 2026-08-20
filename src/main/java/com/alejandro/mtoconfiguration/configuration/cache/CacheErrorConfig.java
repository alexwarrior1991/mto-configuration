package com.alejandro.mtoconfiguration.configuration.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hace que la caché sea una optimización y no una dependencia dura: si Redis no
 * responde, la aplicación sirve desde base de datos en lugar de devolver un 500.
 */
@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    private final RedisCacheAvailability availability;
    private final MeterRegistry meterRegistry;

    public CacheErrorConfig(RedisCacheAvailability availability, MeterRegistry meterRegistry) {
        this.availability = availability;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler(availability, meterRegistry);
    }

    /**
     * Decora el CacheManager que autoconfigura Spring Boot, en lugar de declarar
     * uno propio: así se conservan la autoconfiguración y el
     * {@code RedisCacheManagerBuilderCustomizer} que fija los TTL por caché.
     * <p>
     * Las dependencias entran por {@link ObjectProvider} a propósito: un
     * BeanPostProcessor que las inyecte directamente las obliga a instanciarse
     * antes de tiempo y quedarían fuera del post-procesado del resto del contexto.
     */
    @Bean
    public static BeanPostProcessor resilientCacheManagerPostProcessor(
            ObjectProvider<RedisCacheAvailability> availabilityProvider) {

        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof CacheManager cacheManager && !(bean instanceof ResilientCacheManager)) {
                    return new ResilientCacheManager(cacheManager, availabilityProvider.getObject());
                }

                return bean;
            }
        };
    }
}
