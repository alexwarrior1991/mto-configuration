package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Cierra como fallidos los trabajos que dejaron de latir.
 *
 * <p>Ojo con lo que <b>no</b> hace: no libera cupo. El hueco ya quedo libre en cuanto el latido se
 * enfrio, porque el recuento de ocupacion solo mira a los que laten. Lo que arregla esta pasada es
 * el estado que se le enseña a quien consulta, para que un trabajo cuya replica murio no aparezca
 * eternamente como RUNNING y parezca que sigue avanzando.</p>
 *
 * <p>Esta separacion es la que permite que sea seguro con varias replicas. Marcar RUNNING como
 * fallidos al arrancar —la version ingenua de esto— habria matado los trabajos vivos de las demas
 * replicas; con el latido de por medio, la unica condicion para dar uno por muerto es que lleve un
 * rato sin dar señales, y eso solo lo cumple el que de verdad lo esta.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncJobReaper {

    static final String STALE_MESSAGE =
            "El proceso que ejecutaba el trabajo dejo de dar señales de vida";

    private final AsyncJobRepository repository;
    private final AsyncJobProperties properties;
    private final AsyncJobMetrics metrics;

    @Scheduled(fixedDelayString = "${app.jobs.heartbeat.reaper-fixed-delay:1m}")
    @Transactional
    public void failStaleJobs() {
        Instant now = Instant.now();
        Instant deadSince = now.minus(properties.getHeartbeat().getTimeout());

        try {
            int reaped = repository.failStale(AsyncJobRepository.ACTIVE_STATUSES,
                    JobStatus.FAILED, deadSince, now, STALE_MESSAGE);

            if (reaped > 0) {
                // A nivel WARN a proposito: significa que una replica murio a mitad de un trabajo,
                // y aunque el sistema se recupere solo, es algo que alguien deberia mirar.
                log.warn("Cerrados {} trabajos sin señales de vida desde {}", reaped, deadSince);
                metrics.recordReaped(reaped);
            }
        } catch (Exception e) {
            log.warn("No se han podido cerrar los trabajos sin señales de vida", e);
        }
    }
}
