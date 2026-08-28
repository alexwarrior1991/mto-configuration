package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Borra los trabajos terminados que han superado el periodo de retencion, y sus CSV.
 *
 * <p>Sin esto {@code async_job} solo crece y el directorio de exportacion acumula ficheros para
 * siempre. De los dos, el disco es el que da problemas antes: una exportacion de una via grande son
 * megas, y nadie los borraba.</p>
 *
 * <p>Se purgan solo los TERMINALES. Los vivos se quedan aunque sean viejos: un trabajo que lleva
 * horas corriendo es raro, pero borrarle la fila mientras avanza seria peor que dejarlo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.jobs.purge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncJobPurgeScheduler {

    private final AsyncJobPurgeService purgeService;
    private final ProfileJobFiles files;
    private final AsyncJobProperties properties;

    @Scheduled(fixedDelayString = "${app.jobs.purge.fixed-delay:1h}")
    public void purgeOldJobs() {
        AsyncJobProperties.Purge purge = properties.getPurge();
        Instant threshold = Instant.now().minus(purge.getRetention());

        int purged = 0;

        try {
            for (int batch = 0; batch < purge.getMaxBatchesPerRun(); batch++) {
                int purgedInBatch = purgeService.purgeBatch(threshold, purge.getBatchSize());
                purged += purgedInBatch;

                // Lote incompleto: ya no queda nada mas por debajo del umbral.
                if (purgedInBatch < purge.getBatchSize()) {
                    break;
                }
            }

            // Los huerfanos van aparte porque no tienen fila que los delate: son los CSV a medias
            // que deja una exportacion fallida, cuyo nombre nunca llego a guardarse.
            int orphans = files.deleteOrphansOlderThan(threshold);

            if (purged > 0 || orphans > 0) {
                log.info("Trabajos: purgados {} anteriores a {} (retencion {}) y {} ficheros huerfanos",
                        purged, threshold, formatRetention(purge.getRetention()), orphans);
            }
        } catch (Exception e) {
            log.warn("La purga de trabajos no ha podido completarse", e);
        }
    }

    private String formatRetention(Duration retention) {
        return retention.toDays() > 0 ? retention.toDays() + "d" : retention.toHours() + "h";
    }
}
