package com.alejandro.mtoconfiguration.model.synchronous.lov.imports;

import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Estado de una importacion del catalogo maestro.
 *
 * <p>Es un primo de {@code ProfileJobResponse} sin {@code trackId} ni {@code mapperType},
 * que aqui no significan nada. Se prefiere un tipo propio a reutilizar aquel: sus campos
 * vacios obligarian a quien consume la API a preguntarse si deberia rellenarlos.
 *
 * <p>{@code downloadUrl} es derivado, nunca persistido, y solo aparece cuando hay informe
 * que descargar.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LovImportJobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer totalItems,
        int processedItems,
        int successfulItems,
        int failedItems,
        String downloadUrl,
        String error,
        List<JobItemErrorDTO> itemErrors
) {
}
