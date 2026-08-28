package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobSlotGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refresca la foto de ocupacion que publican las metricas.
 *
 * <p>Va aparte de {@link AsyncJobMetrics} para que esa clase no dependa de la base de datos ni del
 * planificador y se pueda probar sola, igual que en el outbox.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncJobMetricsScheduler {

    private final AsyncJobStore store;
    private final AsyncJobMetrics metrics;

    @Scheduled(fixedDelayString = "${app.jobs.metrics-refresh-delay:30s}")
    public void refreshMetrics() {
        try {
            for (JobSlotGroup group : JobSlotGroup.values()) {
                metrics.updateOccupancy(group, store.countAlive(group), store.maxConcurrencyOf(group));
            }
        } catch (Exception e) {
            // Un fallo midiendo no puede tumbar el planificador, que tambien mueve el latido.
            log.warn("No se ha podido refrescar la ocupacion de los trabajos", e);
        }
    }
}
