package com.alejandro.mtoconfiguration.configuration.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.serializer.SerializationException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Degrada a base de datos cuando Redis no responde, en lugar de propagar el
 * error y devolver un 500.
 * <p>
 * Deliberadamente NO se traga todo. Un handler indiscriminado convierte un bug
 * de programación en «la caché no acierta nunca», que es un síntoma mucho más
 * caro de diagnosticar. Se distinguen tres casos:
 * <ul>
 *   <li><b>Infraestructura</b> (conexión, timeout): se degrada y se arma el
 *       cortocircuito.</li>
 *   <li><b>Serialización</b>: la entrada es ilegible, lo cual es un fallo de
 *       configuración o de tipos, no una caída. Se purga la clave para que no
 *       envenene el endpoint hasta que expire el TTL, se registra a ERROR y se
 *       degrada sólo esta llamada, sin armar el cortocircuito.</li>
 *   <li><b>Cualquier otro</b>: se propaga.</li>
 * </ul>
 * Nota: el fallo de casteo del valor devuelto (por ejemplo un Integer donde se
 * espera un Long) NO pasa por aquí. Ocurre en el proxy, después de que
 * {@code Cache.get} haya terminado, así que sigue propagándose y siendo visible.
 */
public class ResilientCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ResilientCacheErrorHandler.class);

    private static final String METRIC_ERRORS = "cache.errors";

    private final RedisCacheAvailability availability;
    private final MeterRegistry meterRegistry;

    public ResilientCacheErrorHandler(RedisCacheAvailability availability, MeterRegistry meterRegistry) {
        this.availability = availability;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        if (isSerializationProblem(exception)) {
            count(cache, "get", "serialization");
            log.error("Entrada de caché ilegible, se purga. Caché: {}, clave: {}",
                    cache.getName(), key, exception);
            safeEvict(cache, key);
            return;
        }

        degradeOrRethrow(exception, cache, key, "get");
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        degradeOrRethrow(exception, cache, key, "put");
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        degradeOrRethrow(exception, cache, key, "evict");
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        degradeOrRethrow(exception, cache, null, "clear");
    }

    private void degradeOrRethrow(RuntimeException exception, Cache cache, Object key, String operation) {
        if (!isInfrastructureProblem(exception)) {
            // Un fallo de SpEL en la clave, un NPE nuestro... tragarlo escondería un bug.
            count(cache, operation, "unexpected");
            log.error("Error inesperado de caché, se propaga. Caché: {}, clave: {}, operación: {}",
                    cache.getName(), key, operation, exception);
            throw exception;
        }

        count(cache, operation, "infrastructure");
        availability.markDegraded(cache.getName(), exception);
    }

    private boolean isInfrastructureProblem(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof RedisSystemException
                    || current instanceof ConnectException
                    || current instanceof TimeoutException) {
                return true;
            }

            if (current.getCause() == current) {
                break;
            }
        }

        return false;
    }

    private boolean isSerializationProblem(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SerializationException) {
                return true;
            }

            if (current.getCause() == current) {
                break;
            }
        }

        return false;
    }

    private void safeEvict(Cache cache, Object key) {
        try {
            cache.evict(key);
        } catch (RuntimeException suppressed) {
            log.debug("No se pudo purgar la entrada corrupta. Caché: {}, clave: {}",
                    cache.getName(), key, suppressed);
        }
    }

    private void count(Cache cache, String operation, String type) {
        meterRegistry.counter(METRIC_ERRORS,
                "cache", cache.getName(),
                "operation", operation,
                "type", type).increment();
    }
}
