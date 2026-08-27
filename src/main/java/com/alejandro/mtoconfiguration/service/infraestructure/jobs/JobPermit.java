package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Permiso de ejecucion de un trabajo, devuelto al semaforo cuando se cierra.
 *
 * <p>Se adquiere en el hilo de la peticion y se libera en el hilo de fondo, es decir, cruza el
 * salto de hilo. Por eso es un objeto y no un {@code try/finally} local: el {@code finally} de
 * quien lo pide se ejecuta cuando la peticion HTTP ya ha respondido con su 202, mucho antes de que
 * el trabajo termine, y devolver el permiso ahi habria dejado el tope de concurrencia sin efecto.</p>
 *
 * <p>La liberacion es idempotente. Sin el {@link AtomicBoolean}, una doble liberacion (un cierre
 * duplicado, una ruta de error que libera dos veces) <b>anadiria</b> permisos al semaforo:
 * {@code Semaphore.release()} no comprueba nada, de modo que el tope iria creciendo en silencio
 * hasta dejar de existir. Es un fallo que no da la cara hasta que un dia corren veinte
 * exportaciones a la vez.</p>
 */
@Slf4j
public final class JobPermit implements AutoCloseable {

    private final String name;
    private final Semaphore semaphore;
    private final AtomicBoolean released = new AtomicBoolean();

    JobPermit(String name, Semaphore semaphore) {
        this.name = name;
        this.semaphore = semaphore;
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            semaphore.release();
            log.debug("Permiso de ejecucion devuelto [{}], disponibles={}", name, semaphore.availablePermits());
        }
    }
}
