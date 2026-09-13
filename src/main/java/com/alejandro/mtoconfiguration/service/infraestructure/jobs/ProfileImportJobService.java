package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.AsyncConfiguration;
import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Lanza las importaciones del maestro de perfiles.
 *
 * <p>Calca {@link LovImportJobService} porque las razones que lo hacen asi no dependen
 * del dominio: el tope de simultaneidad tiene que ser de todo el despliegue y no de cada
 * replica, y un trabajo que no late deja su hueco cogido para siempre.
 *
 * <p>Vive en este paquete, y no junto al importador en
 * {@code service.infraestructure.imports}, porque necesita {@link ProfileJobProgress},
 * cuyo constructor es de paquete. La logica de dominio esta alli; aqui solo queda la
 * orquestacion del trabajo.
 */
@Slf4j
@Service
public class ProfileImportJobService {

    private final AsyncJobStore store;
    private final AsyncJobHeartbeat heartbeat;
    private final AsyncJobMetrics metrics;
    private final ProfileImportJobRunner runner;
    private final CurrentUserService currentUserService;
    private final AsyncJobProperties properties;
    private final AsyncTaskExecutor taskExecutor;

    public ProfileImportJobService(
            AsyncJobStore store,
            AsyncJobHeartbeat heartbeat,
            AsyncJobMetrics metrics,
            ProfileImportJobRunner runner,
            CurrentUserService currentUserService,
            AsyncJobProperties properties,
            @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor taskExecutor
    ) {
        this.store = store;
        this.heartbeat = heartbeat;
        this.metrics = metrics;
        this.runner = runner;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Encola una importacion.
     *
     * <p>El contenido llega ya en memoria, como {@code byte[]}: el {@code MultipartFile}
     * de la peticion se borra en cuanto esta termina y el hilo de fondo arranca despues,
     * asi que leerlo alli daria un fichero temporal que ya no existe.
     *
     * @param dryRun si es cierto no se escribe nada; el informe se calcula igual
     */
    public ProfileImportJobSubmission submit(byte[] content, boolean dryRun) {
        if (content == null || content.length == 0) {
            throw new ValidationException(List.of(
                    Alert.ofDanger("El fichero del maestro de perfiles es obligatorio", "file")));
        }

        String createdBy = currentUserService.getUsername().orElse(null);

        AsyncJob job = store.createClaimingSlot(JobType.PROFILE_IMPORT, null, null, null, createdBy);
        UUID jobId = job.getId();

        if (job.getStatus() == JobStatus.REJECTED) {
            log.warn("Importacion de perfiles rechazada jobId={} status={}", jobId, JobStatus.REJECTED);
            metrics.recordRejected(JobType.PROFILE_IMPORT);

            return ProfileImportJobSubmission.rejected(job);
        }

        heartbeat.register(jobId);

        try {
            taskExecutor.execute(() -> run(jobId, content, dryRun));
        } catch (RuntimeException e) {
            heartbeat.unregister(jobId);
            store.markFinished(jobId, JobStatus.FAILED, null,
                    "No se pudo encolar el trabajo: " + e.getMessage());
            throw e;
        }

        log.info("Importacion de perfiles aceptada jobId={} dryRun={}", jobId, dryRun);
        metrics.recordAccepted(JobType.PROFILE_IMPORT);

        return ProfileImportJobSubmission.accepted(job);
    }

    /**
     * Ruta del informe. La resuelve el servicio y no el controlador para que el directorio
     * configurado no se filtre a la capa web.
     */
    public Path reportPath(String fileName) {
        return properties.getProfile().getImportReportDirectory().resolve(fileName);
    }

    /**
     * Ejecucion en el hilo de fondo. Aqui no puede escaparse nada: una excepcion que
     * saliera moriria dentro del executor sin traza y dejaria el trabajo en RUNNING para
     * siempre, indistinguible de uno que sigue vivo.
     */
    private void run(UUID jobId, byte[] content, boolean dryRun) {
        long startedAtNanos = System.nanoTime();

        // Fuera del try a proposito: si algo global revienta, el catch todavia tiene los
        // errores por fila ya recogidos, que es cuando mas falta hacen.
        ProfileJobProgress progress = new ProfileJobProgress(
                null, properties.getProfile(), p -> store.saveProgress(jobId, p));

        try {
            store.markRunning(jobId);

            runner.run(jobId, content, dryRun, progress);

            JobStatus finalStatus = progress.getFailedItems() > 0
                    ? JobStatus.COMPLETED_WITH_ERRORS
                    : JobStatus.COMPLETED;

            store.markFinished(jobId, finalStatus, progress, null);
            metrics.recordFinished(JobType.PROFILE_IMPORT, finalStatus, elapsedSince(startedAtNanos));
        } catch (Exception e) {
            log.error("Fallo la importacion de perfiles jobId={}", jobId, e);
            store.markFinished(jobId, JobStatus.FAILED, progress, messageOf(e));
            metrics.recordFinished(JobType.PROFILE_IMPORT, JobStatus.FAILED, elapsedSince(startedAtNanos));
        } finally {
            heartbeat.unregister(jobId);
        }
    }

    private Duration elapsedSince(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    private String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
