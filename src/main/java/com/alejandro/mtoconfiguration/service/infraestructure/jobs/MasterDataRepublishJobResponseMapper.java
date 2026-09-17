package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.MasterDataRepublishJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Convierte la fila del trabajo en la respuesta HTTP del republicado de datos maestros. */
@Component
@RequiredArgsConstructor
public class MasterDataRepublishJobResponseMapper {

    /** Prefijo de los endpoints de republicado. */
    public static final String JOBS_PATH = ConfigurationApiPaths.BASE_PATH + "/master-data/republish";

    private final AsyncJobStore store;

    public MasterDataRepublishJobResponse toResponse(AsyncJob job) {
        List<JobItemErrorDTO> itemErrors = store.readItemErrors(job);

        return new MasterDataRepublishJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getTrackId(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getSuccessfulItems(),
                job.getFailedItems(),
                job.getErrorMessage(),
                itemErrors.isEmpty() ? null : itemErrors
        );
    }

    /** Ruta relativa del estado, usada tambien para la cabecera {@code Location}. */
    public static String statusPath(UUID jobId) {
        return JOBS_PATH + "/" + jobId;
    }
}
