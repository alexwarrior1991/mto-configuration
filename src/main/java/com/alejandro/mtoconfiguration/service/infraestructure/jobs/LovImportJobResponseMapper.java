package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Convierte la fila del trabajo en la respuesta HTTP de la importacion de LOVs. */
@Component
@RequiredArgsConstructor
public class LovImportJobResponseMapper {

    /** Prefijo de los endpoints de importacion de catalogo. */
    public static final String JOBS_PATH = ConfigurationApiPaths.BASE_PATH + "/lovs/jobs";

    private final AsyncJobStore store;

    public LovImportJobResponse toResponse(AsyncJob job) {
        List<JobItemErrorDTO> itemErrors = store.readItemErrors(job);

        return new LovImportJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getSuccessfulItems(),
                job.getFailedItems(),
                downloadUrl(job),
                job.getErrorMessage(),
                itemErrors.isEmpty() ? null : itemErrors
        );
    }

    /** Ruta relativa del estado, usada tambien para la cabecera {@code Location}. */
    public static String statusPath(UUID jobId) {
        return JOBS_PATH + "/" + jobId;
    }

    /**
     * El informe se ofrece en cuanto el trabajo termina, tambien si termino con errores:
     * es precisamente entonces cuando hace falta leerlo. Solo se omite mientras corre,
     * porque el fichero aun no esta escrito.
     */
    private String downloadUrl(AsyncJob job) {
        boolean downloadable = job.getType() == JobType.LOV_IMPORT
                && job.getStatus() != null && job.getStatus().isTerminal()
                && job.getFileName() != null;

        return downloadable ? statusPath(job.getId()) + "/file" : null;
    }
}
