package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.ProfileJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Convierte la fila de un trabajo en su representacion HTTP.
 *
 * <p>Lo unico que no es una copia directa es {@code downloadUrl}, y por eso no se guarda en la
 * tabla: se <b>deriva</b> del tipo y del estado. Persistirla habria significado guardar una URL
 * junto a cada fila y descubrir, el dia que cambia el prefijo de la API, que hay miles de filas
 * apuntando a una ruta que ya no existe.</p>
 */
@Component
@RequiredArgsConstructor
public class ProfileJobResponseMapper {

    /** Prefijo de los endpoints de trabajos de perfiles. */
    public static final String JOBS_PATH = ConfigurationApiPaths.BASE_PATH + "/profiles/jobs";

    private final AsyncJobStore store;

    public ProfileJobResponse toResponse(AsyncJob job) {
        List<JobItemErrorDTO> itemErrors = store.readItemErrors(job);

        return new ProfileJobResponse(
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
                downloadUrl(job),
                job.getErrorMessage(),
                itemErrors.isEmpty() ? null : itemErrors
        );
    }

    /** Ruta relativa del estado del trabajo, usada tambien para la cabecera {@code Location}. */
    public static String statusPath(UUID jobId) {
        return JOBS_PATH + "/" + jobId;
    }

    /**
     * Ruta de descarga, solo cuando hay algo que descargar.
     *
     * <p>Se omite mientras el trabajo corre: ofrecerla antes de tiempo invita al cliente a pedir un
     * fichero a medio escribir, que es peor que no tener enlace.</p>
     */
    private String downloadUrl(AsyncJob job) {
        boolean downloadable = job.getType() == JobType.PROFILE_EXPORT
                && job.getStatus() == JobStatus.COMPLETED
                && job.getFileName() != null;

        return downloadable ? statusPath(job.getId()) + "/file" : null;
    }
}
