package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.AsyncConfiguration;
import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Arranque y seguimiento de los trabajos de perfiles.
 *
 * <h2>Que hace distinta a esta capa</h2>
 *
 * <p>Los endpoints {@code /async} que ya existen devuelven {@code CompletableFuture}: el trabajo
 * cambia de hilo, pero el cliente sigue esperando el resultado final y la conexion HTTP sigue
 * abierta hasta que termina. Si el cliente corta, el resultado no llega a ninguna parte. Eso es
 * concurrencia interna, no una operacion asincrona.</p>
 *
 * <p>Aqui la peticion HTTP no espera a nada: se crea la fila del trabajo, se entrega al executor y
 * se responde 202 con el identificador. El trabajo <b>sobrevive al final de la peticion</b> porque
 * no cuelga de ella —ni de un future que alguien tenga que resolver— sino de una tarea encolada y
 * de una fila en la base de datos que cualquiera puede consultar despues, desde cualquier
 * replica.</p>
 *
 * <h2>Rechazo por capacidad</h2>
 *
 * <p>Cuando no hay hueco se hacen las dos cosas a la vez: se persiste el trabajo como
 * {@link JobStatus#REJECTED} y el controlador responde <b>429</b>. El 429 es lo que un cliente
 * entiende y sabe reintentar; un 202 con un trabajo que nunca va a correr seria mentirle. Y la fila
 * REJECTED es lo que hace el rechazo observable: queda un identificador que consultar y un recuento
 * de cuantas veces se llego al tope, en vez de un mensaje perdido en los logs.</p>
 */
@Slf4j
@Service
public class ProfileJobService {

    private final AsyncJobStore store;
    private final ProfileJobConcurrencyGuard concurrencyGuard;
    private final ProfileExportJobRunner exportRunner;
    private final ProfileBulkJobRunner bulkRunner;
    private final ProfileExportService exportService;
    private final CurrentUserService currentUserService;
    private final AsyncJobProperties properties;

    /**
     * El executor de la aplicacion, no uno propio.
     *
     * <p>Es el mismo bean que usan los {@code @Async}: hilos virtuales y, sobre todo,
     * {@code DelegatingSecurityContextAsyncTaskExecutor}, que copia el {@code SecurityContext} de
     * la peticion al hilo de fondo. Sin esa propagacion el trabajo correria sin identidad, y como
     * el {@code SecurityContextHolder} no protesta por estar vacio, la auditoria de cada perfil
     * creado por una carga masiva se habria atribuido a «system» sin que nadie lo notara.</p>
     */
    private final AsyncTaskExecutor taskExecutor;

    public ProfileJobService(
            AsyncJobStore store,
            ProfileJobConcurrencyGuard concurrencyGuard,
            ProfileExportJobRunner exportRunner,
            ProfileBulkJobRunner bulkRunner,
            ProfileExportService exportService,
            CurrentUserService currentUserService,
            AsyncJobProperties properties,
            @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor taskExecutor
    ) {
        this.store = store;
        this.concurrencyGuard = concurrencyGuard;
        this.exportRunner = exportRunner;
        this.bulkRunner = bulkRunner;
        this.exportService = exportService;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    /** Lanza la exportacion a CSV de los perfiles de una via. */
    public ProfileJobSubmission submitExport(Long trackId, String mapperType) {
        String canonicalMapper = exportService.resolveMapperName(mapperType);

        return submit(JobType.PROFILE_EXPORT, trackId, canonicalMapper, null,
                (jobId, progress) -> exportRunner.run(jobId, trackId, canonicalMapper, progress));
    }

    /** Lanza el alta masiva de perfiles. */
    public ProfileJobSubmission submitBulkCreate(List<ProfileDTO> dtoList) {
        return submitBulk(JobType.PROFILE_BULK_CREATE, dtoList);
    }

    /** Lanza la modificacion masiva de perfiles. */
    public ProfileJobSubmission submitBulkUpdate(List<ProfileDTO> dtoList) {
        return submitBulk(JobType.PROFILE_BULK_UPDATE, dtoList);
    }

    /** Trabajo por identificador. */
    public AsyncJob getJob(UUID jobId) {
        return store.findById(jobId)
                .orElseThrow(() -> new NotFoundException("No existe el trabajo " + jobId));
    }

    private ProfileJobSubmission submitBulk(JobType type, List<ProfileDTO> dtoList) {
        // Un lote vacio se rechaza aqui y no se convierte en un trabajo. Aceptarlo habria creado
        // una fila que nace y muere COMPLETED sin haber hecho nada, y el cliente se llevaria un 202
        // por una peticion que casi siempre es un error suyo. Sale como ValidationException para
        // que viaje con el mismo cuerpo Problem Details que el resto de errores de la API.
        if (dtoList == null || dtoList.isEmpty()) {
            throw new ValidationException(List.of(
                    Alert.ofDanger("La lista de perfiles no puede estar vacia", "dtoList")));
        }

        // Se copia la lista: el trabajo la va a recorrer mucho despues de que termine la peticion
        // que la trajo, y la deserializada por el controlador no tiene por que seguir intacta ni
        // ser segura de leer desde otro hilo.
        List<ProfileDTO> items = List.copyOf(dtoList);

        return submit(type, null, null, items.size(),
                (jobId, progress) -> bulkRunner.run(jobId, type, items, progress));
    }

    private ProfileJobSubmission submit(JobType type,
                                        Long trackId,
                                        String mapperType,
                                        Integer totalItems,
                                        JobTask task) {
        // La identidad se captura AQUI, en el hilo de la peticion. En el hilo de fondo el
        // SecurityContext llega propagado por el executor, pero capturarlo antes deja claro que el
        // trabajo pertenece a quien lo pidio y no a quien lo ejecuta.
        String createdBy = currentUserService.getUsername().orElse(null);

        Optional<JobPermit> permit = concurrencyGuard.tryAcquire(type);

        if (permit.isEmpty()) {
            AsyncJob rejected = store.create(type, JobStatus.REJECTED, trackId, mapperType, totalItems,
                    createdBy, "Sin capacidad para ejecutar mas trabajos de tipo " + type);

            log.warn("Trabajo rechazado jobId={} type={} status={}",
                    rejected.getId(), type, JobStatus.REJECTED);

            return ProfileJobSubmission.rejected(rejected);
        }

        JobPermit acquired = permit.get();
        AsyncJob job;

        try {
            job = store.create(type, JobStatus.PENDING, trackId, mapperType, totalItems, createdBy, null);
        } catch (RuntimeException e) {
            // Si no se pudo ni crear la fila, el permiso no lo va a devolver nadie mas.
            acquired.close();
            throw e;
        }

        UUID jobId = job.getId();

        try {
            taskExecutor.execute(() -> run(jobId, type, acquired, task));
        } catch (RuntimeException e) {
            acquired.close();
            store.markFinished(jobId, JobStatus.FAILED, null,
                    "No se pudo encolar el trabajo: " + e.getMessage());
            throw e;
        }

        log.info("Trabajo aceptado jobId={} type={} status={}", jobId, type, JobStatus.PENDING);
        return ProfileJobSubmission.accepted(job);
    }

    /**
     * Ejecucion en el hilo de fondo. Aqui no puede escaparse nada.
     *
     * <p>Es el ultimo punto con contexto para dejar constancia de lo que paso: una excepcion que
     * saliera de este metodo moriria dentro del executor sin traza util, y el trabajo se quedaria
     * en RUNNING para siempre, indistinguible de uno que sigue corriendo.</p>
     */
    private void run(UUID jobId, JobType type, JobPermit permit, JobTask task) {
        // try-with-resources sobre el permiso: se devuelve pase lo que pase, tambien si el hilo
        // muere por un Error. Un permiso no devuelto reduce el tope de forma permanente, y el
        // sintoma —los trabajos empiezan a rechazarse sin motivo aparente— aparece mucho despues
        // de la causa.
        try (permit) {
            store.markRunning(jobId);

            ProfileJobProgress progress = new ProfileJobProgress(
                    null, properties.getProfile(), p -> store.saveProgress(jobId, p));

            task.execute(jobId, progress);

            // El ultimo tramo puede no haberse volcado: el volcado periodico solo salta cada N.
            JobStatus finalStatus = progress.getFailedItems() > 0
                    ? JobStatus.COMPLETED_WITH_ERRORS
                    : JobStatus.COMPLETED;

            store.markFinished(jobId, finalStatus, progress, null);
        } catch (InterruptedException e) {
            // Restaurar el flag es obligatorio: se acaba de consumir la interrupcion al capturarla,
            // y sin reponerla el resto del hilo —y quien lo gestione— dejaria de enterarse de que
            // alguien pidio parar.
            Thread.currentThread().interrupt();
            failJob(jobId, type, e);
        } catch (Exception e) {
            failJob(jobId, type, e);
        }
    }

    private void failJob(UUID jobId, JobType type, Exception e) {
        log.error("Trabajo fallido jobId={} type={} status={}", jobId, type, JobStatus.FAILED, e);

        try {
            store.markFinished(jobId, JobStatus.FAILED, null, describeFailure(e));
        } catch (RuntimeException persistenceFailure) {
            // Si tampoco se puede escribir el fallo, el log es lo unico que queda. No se propaga:
            // el hilo ya esta en su camino de salida y relanzar solo perderia esta traza.
            log.error("Ademas, no se pudo registrar el fallo del trabajo jobId={}", jobId, persistenceFailure);
        }
    }

    /** Mensaje del fallo con su clase, porque un {@code getMessage()} nulo no dice nada. */
    private String describeFailure(Exception e) {
        return e.getMessage() != null
                ? "%s: %s".formatted(e.getClass().getSimpleName(), e.getMessage())
                : e.getClass().getSimpleName();
    }

    /** Trabajo concreto, ya con su identificador resuelto. */
    @FunctionalInterface
    private interface JobTask {
        void execute(UUID jobId, ProfileJobProgress progress) throws Exception;
    }
}
