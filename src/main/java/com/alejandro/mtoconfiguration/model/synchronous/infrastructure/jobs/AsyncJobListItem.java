package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del listado de trabajos ({@code GET /jobs}): lo que tienen en comun las tres familias,
 * sin los errores por elemento ni la ruta de descarga, que son del detalle de cada familia
 * ({@code GET /{familia}/{jobId}}). Los campos que no aplican se omiten, como en el detalle.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsyncJobListItem(
        UUID id,
        JobType type,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long trackId,
        String mapperType,
        Integer totalItems,
        int processedItems,
        int successfulItems,
        int failedItems,
        String error
) {

    public static AsyncJobListItem of(AsyncJob job) {
        return new AsyncJobListItem(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getTrackId(),
                job.getMapperType(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getSuccessfulItems(),
                job.getFailedItems(),
                job.getErrorMessage()
        );
    }
}
