package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.AsyncConfiguration;
import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Lanza y consulta las importaciones del catalogo maestro de LOVs.
 *
 * <p>Calca el ciclo de {@link ProfileJobService} —reservar cupo y crear la fila en la
 * misma transaccion, registrar el latido antes de encolar, ejecutar en el executor de
 * la aplicacion— porque las razones que lo hacen asi no dependen del dominio: el tope
 * de simultaneidad tiene que ser de todo el despliegue y no de cada replica, y un
 * trabajo que no late deja su hueco cogido para siempre.
 *
 * <p>Vive en este paquete, y no junto al resto del importador en
 * {@code service.lov.imports}, porque necesita {@link ProfileJobProgress}, cuyo
 * constructor es de paquete. La logica de dominio —leer el maestro y hacer el upsert—
 * si esta alli; aqui solo queda la orquestacion del trabajo.
 */
@Slf4j
@Service
public class LovImportJobService {

    private final AsyncJobStore store;
    private final AsyncJobHeartbeat heartbeat;
    private final AsyncJobMetrics metrics;
    private final LovImportJobRunner runner;
    private final CurrentUserService currentUserService;
    private final AsyncJobProperties properties;
    private final AsyncTaskExecutor taskExecutor;

    public LovImportJobService(
            AsyncJobStore store,
            AsyncJobHeartbeat heartbeat,
            AsyncJobMetrics metrics,
            LovImportJobRunner runner,
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
     * <p>El contenido del fichero llega ya en memoria, como {@code byte[]}: el
     * {@code MultipartFile} de la peticion se borra en cuanto esta termina, y el hilo de
     * fondo arranca despues. Leerlo alli daria un fichero temporal que ya no existe.
     *
     * @param dryRun si es cierto no se escribe nada; el informe se calcula igual
     */
    public LovImportJobSubmission submit(byte[] content, boolean dryRun) {
        if (content == null || content.length == 0) {
            throw new ValidationException(List.of(
                    Alert.ofDanger("El fichero del catalogo maestro es obligatorio", "file")));
        }

        String createdBy = currentUserService.getUsername().orElse(null);

        AsyncJob job = store.createClaimingSlot(JobType.LOV_IMPORT, null, null, null, createdBy);
        UUID jobId = job.getId();

        if (job.getStatus() == JobStatus.REJECTED) {
            log.warn("Importacion de LOV rechazada jobId={} status={}", jobId, JobStatus.REJECTED);
            metrics.recordRejected(JobType.LOV_IMPORT);

            return LovImportJobSubmission.rejected(job);
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

        log.info("Importacion de LOV aceptada jobId={} dryRun={}", jobId, dryRun);
        metrics.recordAccepted(JobType.LOV_IMPORT);

        return LovImportJobSubmission.accepted(job);
    }

    /**
     * Ruta del informe de un trabajo. La resuelve el servicio y no el controlador para que
     * el directorio configurado no se filtre a la capa web.
     */
    public java.nio.file.Path reportPath(String fileName) {
        return properties.getLov().getReportDirectory().resolve(fileName);
    }

    public AsyncJob getJob(UUID jobId) {
        if (jobId == null) {
            throw new ValidationException(List.of(
                    Alert.ofDanger("El identificador del trabajo es obligatorio", "jobId")));
        }

        return store.findById(jobId)
                .orElseThrow(() -> new NotFoundException("No existe el trabajo " + jobId));
    }

    /**
     * Ejecucion en el hilo de fondo. Aqui no puede escaparse nada: una excepcion que
     * saliera moriria dentro del executor sin traza y dejaria el trabajo en RUNNING para
     * siempre, indistinguible de uno que sigue vivo.
     */
    private void run(UUID jobId, byte[] content, boolean dryRun) {
        long startedAtNanos = System.nanoTime();

        // Fuera del try a proposito, igual que en ProfileJobService: si algo global revienta,
        // el catch todavia tiene los errores por fila ya recogidos, que es cuando mas falta hacen.
        ProfileJobProgress progress = new ProfileJobProgress(
                null, properties.getProfile(), p -> store.saveProgress(jobId, p));

        try {
            store.markRunning(jobId);

            runner.run(jobId, content, dryRun, progress);

            JobStatus finalStatus = progress.getFailedItems() > 0
                    ? JobStatus.COMPLETED_WITH_ERRORS
                    : JobStatus.COMPLETED;

            store.markFinished(jobId, finalStatus, progress, null);
            metrics.recordFinished(JobType.LOV_IMPORT, finalStatus, elapsedSince(startedAtNanos));
        } catch (Exception e) {
            log.error("Fallo la importacion de LOV jobId={}", jobId, e);
            store.markFinished(jobId, JobStatus.FAILED, progress, messageOf(e));
            metrics.recordFinished(JobType.LOV_IMPORT, JobStatus.FAILED, elapsedSince(startedAtNanos));
        } finally {
            heartbeat.unregister(jobId);
        }
    }

    private java.time.Duration elapsedSince(long startedAtNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    private String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
