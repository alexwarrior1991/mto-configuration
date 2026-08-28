package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Señal de vida de los trabajos que ejecuta <b>esta</b> replica.
 *
 * <p>Es la pieza que hace viable un tope de concurrencia de clúster. El cupo lo ocupan los trabajos
 * que laten, no los que tienen la fila en RUNNING: asi, una replica que muere a mitad de una
 * exportacion deja de latir y su hueco vuelve solo, sin que nadie tenga que ir a limpiarlo ni
 * arriesgarse a matar el trabajo de otra replica que si esta viva.</p>
 *
 * <p>El latido es <b>independiente del progreso</b>, y esa separacion es deliberada. Atarlo al
 * volcado de contadores habria sido gratis, pero un trabajo cuyo elemento en curso tarda cinco
 * minutos —un update pesado, una consulta atascada— habria dejado de latir estando perfectamente
 * vivo, y otra replica se habria llevado su hueco mientras el seguia trabajando.</p>
 *
 * <p>Un solo UPDATE por pasada para todos los trabajos de la replica, asi que el coste no crece con
 * el numero de trabajos en curso.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncJobHeartbeat {

    private final AsyncJobRepository repository;

    /**
     * Trabajos en curso en este proceso.
     *
     * <p>Concurrente porque lo tocan a la vez el hilo de la peticion que da de alta el trabajo, el
     * hilo de fondo que lo cierra y el del planificador que lo recorre.</p>
     */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    void register(UUID jobId) {
        inFlight.add(jobId);
    }

    void unregister(UUID jobId) {
        inFlight.remove(jobId);
    }

    /** Trabajos que esta replica dice estar ejecutando. Para metricas y pruebas. */
    public int inFlightCount() {
        return inFlight.size();
    }

    @Scheduled(fixedDelayString = "${app.jobs.heartbeat.interval:15s}")
    @Transactional
    public void beat() {
        if (inFlight.isEmpty()) {
            return;
        }

        // Copia: el conjunto cambia mientras se consulta, y pasarlo directo a un "in (...)" que
        // muta a media construccion es pedir un problema raro y dificil de reproducir.
        Set<UUID> snapshot = Set.copyOf(inFlight);

        try {
            int refreshed = repository.heartbeat(snapshot, AsyncJobRepository.ACTIVE_STATUSES, Instant.now());
            log.debug("Latido de {} trabajos en curso ({} filas refrescadas)", snapshot.size(), refreshed);
        } catch (Exception e) {
            // Un fallo aqui no puede tumbar el planificador. Si la base de datos no responde, el
            // latido se enfria y el trabajo acabara dandose por muerto: es el comportamiento
            // correcto, porque un trabajo que no puede escribir tampoco esta avanzando.
            log.warn("No se ha podido refrescar el latido de {} trabajos en curso", snapshot.size(), e);
        }
    }
}
