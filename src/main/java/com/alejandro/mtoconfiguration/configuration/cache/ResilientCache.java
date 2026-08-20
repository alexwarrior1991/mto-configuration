package com.alejandro.mtoconfiguration.configuration.cache;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Envoltorio que cortocircuita las llamadas a Redis mientras la caché esté
 * degradada, para no pagar el timeout de conexión en cada petición.
 * <p>
 * Cuando está cortocircuitada, las lecturas devuelven un fallo de caché (null) y
 * las escrituras e invalidaciones no hacen nada: Spring ejecuta el método y sirve
 * desde base de datos. Cuando no lo está, delega y marca la caché como sana, que
 * es como se sale del estado degradado.
 */
public class ResilientCache implements Cache {

    private final Cache delegate;
    private final RedisCacheAvailability availability;

    public ResilientCache(Cache delegate, RedisCacheAvailability availability) {
        this.delegate = delegate;
        this.availability = availability;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public @Nullable ValueWrapper get(Object key) {
        if (!availability.isAvailable()) {
            return null;
        }

        ValueWrapper value = delegate.get(key);
        availability.markHealthy();
        return value;
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        if (!availability.isAvailable()) {
            return null;
        }

        T value = delegate.get(key, type);
        availability.markHealthy();
        return value;
    }

    @Override
    public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
        if (!availability.isAvailable()) {
            return loadDirectly(valueLoader);
        }

        T value = delegate.get(key, valueLoader);
        availability.markHealthy();
        return value;
    }

    @Override
    public @Nullable CompletableFuture<?> retrieve(Object key) {
        if (!availability.isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }

        return delegate.retrieve(key);
    }

    @Override
    public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
        if (!availability.isAvailable()) {
            return valueLoader.get();
        }

        return delegate.retrieve(key, valueLoader);
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (!availability.isAvailable()) {
            return;
        }

        delegate.put(key, value);
        availability.markHealthy();
    }

    @Override
    public @Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        if (!availability.isAvailable()) {
            return null;
        }

        ValueWrapper previous = delegate.putIfAbsent(key, value);
        availability.markHealthy();
        return previous;
    }

    @Override
    public void evict(Object key) {
        if (!availability.isAvailable()) {
            return;
        }

        delegate.evict(key);
        availability.markHealthy();
    }

    @Override
    public boolean evictIfPresent(Object key) {
        if (!availability.isAvailable()) {
            return false;
        }

        boolean evicted = delegate.evictIfPresent(key);
        availability.markHealthy();
        return evicted;
    }

    @Override
    public void clear() {
        if (!availability.isAvailable()) {
            return;
        }

        delegate.clear();
        availability.markHealthy();
    }

    @Override
    public boolean invalidate() {
        if (!availability.isAvailable()) {
            return false;
        }

        boolean invalidated = delegate.invalidate();
        availability.markHealthy();
        return invalidated;
    }

    private <T> T loadDirectly(Callable<T> valueLoader) {
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(null, valueLoader, e);
        }
    }
}
