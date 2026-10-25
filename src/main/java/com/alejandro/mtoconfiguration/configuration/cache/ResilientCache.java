package com.alejandro.mtoconfiguration.configuration.cache;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>
 * Además deshace el {@code put} de una lectura que cargó mientras una escritura
 * vaciaba la caché ({@link CacheGeneration}, que explica la carrera). Un
 * {@code @Cacheable} sin {@code sync} hace, en el mismo hilo, {@link #get(Object)}
 * con fallo, el método y {@link #put}: el fallo apunta la generación de esa clave
 * en el hilo, y el {@code put} guarda, vuelve a leerla y, si subió, borra lo que
 * acaba de guardar. Un {@code put} sin fallo antes en el hilo (nadie lo hace hoy:
 * no hay {@code @CachePut} ni {@code sync}) guarda sin más, como antes.
 */
public class ResilientCache implements Cache {

    /**
     * Cuántos fallos sin su {@code put} recuerda un hilo. Un {@code unless} que no guarda o un método
     * que lanza dejan el suyo; no estorba (el siguiente fallo de la misma clave lo pisa), y este tope
     * evita que se acumulen en un hilo de un pool. Sobra para los {@code @Cacheable} anidados.
     */
    private static final int MISSES_PER_THREAD = 16;

    private final Cache delegate;
    private final RedisCacheAvailability availability;
    private final CacheGeneration generation;

    /** La generación que vio cada fallo de este hilo, por clave, hasta su {@code put}. */
    private final ThreadLocal<Map<Object, Long>> misses = ThreadLocal.withInitial(() ->
            new LinkedHashMap<>(MISSES_PER_THREAD, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Object, Long> eldest) {
                    return size() > MISSES_PER_THREAD;
                }
            });

    public ResilientCache(Cache delegate, RedisCacheAvailability availability, CacheGeneration generation) {
        this.delegate = delegate;
        this.availability = availability;
        this.generation = generation;
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
        if (value == null) {
            rememberMiss(key);
        }
        return value;
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        if (!availability.isAvailable()) {
            return null;
        }

        T value = delegate.get(key, type);
        availability.markHealthy();
        if (value == null) {
            rememberMiss(key);
        }
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
        Long seen = misses.get().remove(key);
        if (!availability.isAvailable()) {
            return;
        }

        delegate.put(key, value);
        availability.markHealthy();
        if (seen != null && generation.current() != seen) {
            // Una escritura vació la caché mientras se cargaba: lo guardado puede ser de antes.
            // Si esta lectura de la generación falla, lo guardado se queda hasta el TTL.
            delegate.evict(key);
        }
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

    /**
     * Apunta la generación al fallar, antes de que el método lea la base: lo que lea después
     * de una escritura que la suba ya es de después de esa escritura.
     */
    private void rememberMiss(Object key) {
        misses.get().put(key, generation.current());
    }

    private <T> T loadDirectly(Callable<T> valueLoader) {
        try {
            return valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(null, valueLoader, e);
        }
    }
}
