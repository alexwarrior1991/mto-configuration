package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado de un republicado de datos maestros.
 *
 * <p>Tipo propio y no {@code ProfileJobResponse} por lo mismo que la importacion de LOVs tiene el
 * suyo: {@code mapperType} y {@code downloadUrl} no significan nada aqui —este trabajo no produce
 * fichero— y unos campos siempre vacios obligan a quien consume la API a preguntarse si deberia
 * rellenarlos.
 *
 * <p>{@code trackId} si esta, porque la columna existe y significa exactamente eso. La entidad
 * republicada y el {@code stationId} no viajan: {@code async_job} no tiene columnas para ellos y
 * añadirlas para un trabajo puntual no lo justifica; quedan en el log de arranque del trabajo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MasterDataRepublishJobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long trackId,
        Integer totalItems,
        int processedItems,
        int successfulItems,
        int failedItems,
        String error,
        List<JobItemErrorDTO> itemErrors
) {
}
