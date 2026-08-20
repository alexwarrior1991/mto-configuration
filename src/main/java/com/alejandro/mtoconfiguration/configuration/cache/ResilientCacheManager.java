package com.alejandro.mtoconfiguration.configuration.cache;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Envuelve cada {@link Cache} del CacheManager delegado en un {@link ResilientCache}.
 */
public class ResilientCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final RedisCacheAvailability availability;
    private final Map<String, Cache> wrappers = new ConcurrentHashMap<>();

    public ResilientCacheManager(CacheManager delegate, RedisCacheAvailability availability) {
        this.delegate = delegate;
        this.availability = availability;
    }

    @Override
    public @Nullable Cache getCache(String name) {
        Cache cache = delegate.getCache(name);

        if (cache == null) {
            return null;
        }

        return wrappers.computeIfAbsent(name, ignored -> new ResilientCache(cache, availability));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
