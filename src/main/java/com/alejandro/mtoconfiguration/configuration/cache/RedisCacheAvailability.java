package com.alejandro.mtoconfiguration.configuration.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Estado compartido del cortocircuito de la caché Redis.
 * <p>
 * El {@link org.springframework.cache.interceptor.CacheErrorHandler} sólo se
 * invoca DESPUÉS de que la excepción se haya producido: evita el 500, pero el
 * viaje a Redis y su timeout ya se han pagado. Para evitar también el coste hay
 * que cortocircuitar antes de llamar a Redis, y de eso se encarga
 * {@link ResilientCache} leyendo este estado.
 * <p>
 * Pasada la ventana de reintento el estado se considera sano por sí solo, de modo
 * que la siguiente petición vuelve a probar Redis. No hace falta ningún hilo de
 * fondo: si el reintento falla, el handler vuelve a armar el cortocircuito.
 */
@Component
public class RedisCacheAvailability {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheAvailability.class);

    private final Duration retryWindow;

    /** Instante (epoch millis) hasta el que la caché se considera degradada. 0 = sana. */
    private final AtomicLong degradedUntilEpochMs = new AtomicLong(0);

    public RedisCacheAvailability(RedisCacheProperties properties) {
        this.retryWindow = properties.getDegradedRetryWindow();
    }

    public boolean isAvailable() {
        return System.currentTimeMillis() >= degradedUntilEpochMs.get();
    }

    public Duration getRetryWindow() {
        return retryWindow;
    }

    public void markDegraded(String cacheName, Throwable cause) {
        long now = System.currentTimeMillis();
        long previousDegradedUntil = degradedUntilEpochMs.getAndSet(now + retryWindow.toMillis());

        if (previousDegradedUntil < now) {
            // Primera caída, o primera tras haberse recuperado: traza completa una sola vez.
            // Sin esta condición habría un stacktrace por petición y el log sería inservible.
            log.warn("Caché Redis degradada durante {}. Sirviendo desde base de datos. Caché: {}",
                    retryWindow, cacheName, cause);
        } else {
            log.debug("La caché Redis sigue degradada. Caché: {}", cacheName);
        }
    }

    public void markHealthy() {
        // Lectura primero: evita un CAS en cada operación correcta, que es el caso normal.
        if (degradedUntilEpochMs.get() != 0 && degradedUntilEpochMs.getAndSet(0) != 0) {
            log.warn("Caché Redis de nuevo disponible.");
        }
    }
}
