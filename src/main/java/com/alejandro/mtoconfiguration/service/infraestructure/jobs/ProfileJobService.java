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

import java.time.Duration;
import java.util.List;
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
    private final AsyncJobHeartbeat heartbeat;
    private final AsyncJobMetrics metrics;
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
            AsyncJobHeartbeat heartbeat,
            AsyncJobMetrics metrics,
            ProfileExportJobRunner exportRunner,
            ProfileBulkJobRunner bulkRunner,
            ProfileExportService exportService,
            CurrentUserService currentUserService,
            AsyncJobProperties properties,
            @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor taskExecutor
    ) {
        this.store = store;
        this.heartbeat = heartbeat;
        this.metrics = metrics;
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

        int maxItems = properties.getProfile().getMaxBulkItems();

        // El tope llega tarde y conviene reconocerlo: para cuando se comprueba, el cuerpo entero ya
        // esta deserializado en memoria en el hilo de la peticion, asi que no protege del cliente
        // que manda un fichero de un giga. Lo que si hace es convertir en un 400 explicito lo que
        // de otro modo seria un trabajo de horas que nadie pidio conscientemente. La proteccion de
        // verdad es un limite de tamaño de peticion en el proxy de entrada.
        if (dtoList.size() > maxItems) {
            throw new ValidationException(List.of(Alert.ofDanger(
                    "La carga masiva admite como maximo %d elementos y se han enviado %d"
                            .formatted(maxItems, dtoList.size()), "dtoList")));
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

        // Reservar cupo y crear la fila son la MISMA transaccion, y tienen que serlo: separarlas
        // reabre la carrera que el cerrojo evita —dos peticiones simultaneas viendo el mismo hueco
        // libre— y dejaria ademas una ventana en la que el hueco esta cogido y no hay nada que lo
        // suelte si la creacion falla.
        AsyncJob job = store.createClaimingSlot(type, trackId, mapperType, totalItems, createdBy);
        UUID jobId = job.getId();

        if (job.getStatus() == JobStatus.REJECTED) {
            log.warn("Trabajo rechazado jobId={} type={} status={}", jobId, type, JobStatus.REJECTED);
            metrics.recordRejected(type);

            return ProfileJobSubmission.rejected(job);
        }

        // Se registra ANTES de encolar: si el latido no empieza ya, el trabajo podria enfriarse
        // entre que se confirma la fila y arranca el hilo de fondo.
        heartbeat.register(jobId);

        try {
            taskExecutor.execute(() -> run(jobId, type, task));
        } catch (RuntimeException e) {
            heartbeat.unregister(jobId);
            store.markFinished(jobId, JobStatus.FAILED, null,
                    "No se pudo encolar el trabajo: " + e.getMessage());
            throw e;
        }

        log.info("Trabajo aceptado jobId={} type={} status={}", jobId, type, JobStatus.PENDING);
        metrics.recordAccepted(type);

        return ProfileJobSubmission.accepted(job);
    }

    /**
     * Ejecucion en el hilo de fondo. Aqui no puede escaparse nada.
     *
     * <p>Es el ultimo punto con contexto para dejar constancia de lo que paso: una excepcion que
     * saliera de este metodo moriria dentro del executor sin traza util, y el trabajo se quedaria
     * en RUNNING para siempre, indistinguible de uno que sigue corriendo.</p>
     */
    private void run(UUID jobId, JobType type, JobTask task) {
        long startedAtNanos = System.nanoTime();

        // El progreso se crea FUERA del try para que el catch tambien lo vea. Estaba dentro, y eso
        // hacia que un fallo global tirara los errores por elemento ya recogidos: una carga que
        // procesa nueve mil elementos, acumula sus fallos y revienta por algo global terminaba con
        // el detalle vacio, que es justo cuando mas falta hace.
        ProfileJobProgress progress = new ProfileJobProgress(
                null, properties.getProfile(), p -> store.saveProgress(jobId, p));

        // El finally quita el trabajo del latido pase lo que pase, tambien si el hilo muere por un
        // Error. Olvidarlo ya no cuesta un hueco para siempre —eso era el semaforo—, pero si deja a
        // la replica refrescando el latido de un trabajo que termino hace rato.
        try {
            store.markRunning(jobId);

            task.execute(jobId, progress);

            // El ultimo tramo puede no haberse volcado: el volcado periodico solo salta cada N.
            JobStatus finalStatus = progress.getFailedItems() > 0
                    ? JobStatus.COMPLETED_WITH_ERRORS
                    : JobStatus.COMPLETED;

            store.markFinished(jobId, finalStatus, progress, null);
            metrics.recordFinished(type, finalStatus, elapsedSince(startedAtNanos));
        } catch (InterruptedException e) {
            // Restaurar el flag es obligatorio: se acaba de consumir la interrupcion al capturarla,
            // y sin reponerla el resto del hilo —y quien lo gestione— dejaria de enterarse de que
            // alguien pidio parar.
            Thread.currentThread().interrupt();
            failJob(jobId, type, progress, e, startedAtNanos);
        } catch (Exception e) {
            failJob(jobId, type, progress, e, startedAtNanos);
        } finally {
            heartbeat.unregister(jobId);
        }
    }

    private Duration elapsedSince(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    /**
     * Cierra el trabajo como fallido <b>conservando lo que se llego a hacer</b>.
     *
     * <p>El progreso viaja tambien aqui: un fallo global no invalida los elementos ya procesados ni
     * los errores ya diagnosticados, y son lo unico que permite saber por donde iba el trabajo
     * cuando se cayo. En una exportacion el nombre del fichero sigue sin escribirse —solo se fija
     * cuando el CSV esta completo—, asi que un volcado a medias no se ofrece nunca para descarga.</p>
     */
    private void failJob(UUID jobId, JobType type, ProfileJobProgress progress, Exception e,
                         long startedAtNanos) {
        log.error("Trabajo fallido jobId={} type={} status={} procesados={}",
                jobId, type, JobStatus.FAILED, progress.getProcessedItems(), e);

        try {
            store.markFinished(jobId, JobStatus.FAILED, progress, describeFailure(e));
            metrics.recordFinished(type, JobStatus.FAILED, elapsedSince(startedAtNanos));
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
