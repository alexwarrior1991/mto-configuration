package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.AsyncConfiguration;
import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.enums.jobs.MasterDataRepublishTarget;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Arranque, salto de hilo y cierre del republicado de datos maestros.
 *
 * <p>Mismo ciclo que {@code ProfileJobService}: reserva de cupo, 202 o 429, salto al executor de la
 * aplicacion y cierre del trabajo. Vive en este paquete porque los constructores de
 * {@link ProfileJobProgress} son package-private.</p>
 *
 * <h2>Por que aqui y no un API que tire el consumidor</h2>
 *
 * <p>Porque el consumidor descarta por marca de agua lo que llega con un numero de secuencia menor
 * del que ya aplico, y ese numero lo asigna la base <b>al escribir la fila de outbox</b>. Un API de
 * lectura que el consumidor recorriera no tendria ningun numero que escribir, asi que el dato
 * entraria sin marca de agua y el siguiente evento real podria quedar por debajo.</p>
 *
 * <h2>Por que asincrono</h2>
 *
 * <p>Porque una seleccion de miles de perfiles se pasa del {@code GATEWAY_READ_TIMEOUT} de 15s de
 * {@code mto-gateway}. Con 202 + {@code jobId} la peticion se cierra en milisegundos y el trabajo
 * sigue aunque el cliente se desconecte.</p>
 */
@Slf4j
@Service
public class MasterDataRepublishJobService {

    private final AsyncJobStore store;
    private final AsyncJobHeartbeat heartbeat;
    private final AsyncJobMetrics metrics;
    private final MasterDataRepublishJobRunner runner;
    private final CurrentUserService currentUserService;
    private final AsyncJobProperties properties;
    private final AsyncTaskExecutor taskExecutor;
    private final ProfileRepository profileRepository;
    private final DisconnectorRepository disconnectorRepository;
    private final SectionInsulatorRepository sectionInsulatorRepository;

    public MasterDataRepublishJobService(
            AsyncJobStore store,
            AsyncJobHeartbeat heartbeat,
            AsyncJobMetrics metrics,
            MasterDataRepublishJobRunner runner,
            CurrentUserService currentUserService,
            AsyncJobProperties properties,
            @Qualifier(AsyncConfiguration.TASK_EXECUTOR) AsyncTaskExecutor taskExecutor,
            ProfileRepository profileRepository,
            DisconnectorRepository disconnectorRepository,
            SectionInsulatorRepository sectionInsulatorRepository
    ) {
        this.store = store;
        this.heartbeat = heartbeat;
        this.metrics = metrics;
        this.runner = runner;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
        this.profileRepository = profileRepository;
        this.disconnectorRepository = disconnectorRepository;
        this.sectionInsulatorRepository = sectionInsulatorRepository;
    }

    /**
     * Encola un republicado.
     *
     * @param entity    {@code profile}, {@code disconnector}, {@code section-insulator} o
     *                  {@code all}
     * @param trackId   solo con {@code profile}
     * @param stationId solo con {@code disconnector} y {@code section-insulator}
     */
    public MasterDataRepublishJobSubmission submit(String entity, Long trackId, Long stationId) {
        MasterDataRepublishTarget target = validate(entity, trackId, stationId);

        int totalItems = countItems(target, trackId, stationId);

        String createdBy = currentUserService.getUsername().orElse(null);

        // El trackId si va a su columna, que significa exactamente eso. El resto de la seleccion
        // —la entidad y el stationId— se queda en el log: async_job no tiene columnas para ellos y
        // añadirlas para un trabajo puntual no lo justifica. Mismo criterio que el dryRun de la
        // importacion del maestro de perfiles.
        AsyncJob job = store.createClaimingSlot(
                JobType.MASTER_DATA_REPUBLISH, trackId, null, totalItems, createdBy);
        UUID jobId = job.getId();

        if (job.getStatus() == JobStatus.REJECTED) {
            log.warn("Republicado rechazado jobId={} entity={} status={}",
                    jobId, target.getParameter(), JobStatus.REJECTED);
            metrics.recordRejected(JobType.MASTER_DATA_REPUBLISH);

            return MasterDataRepublishJobSubmission.rejected(job);
        }

        // Se registra ANTES de encolar: si el latido no empieza ya, el trabajo podria enfriarse
        // entre que se confirma la fila y arranca el hilo de fondo.
        heartbeat.register(jobId);

        try {
            taskExecutor.execute(() -> run(jobId, target, trackId, stationId, totalItems));
        } catch (RuntimeException e) {
            heartbeat.unregister(jobId);
            store.markFinished(jobId, JobStatus.FAILED, null,
                    "No se pudo encolar el trabajo: " + e.getMessage());
            throw e;
        }

        log.info("Republicado aceptado jobId={} entity={} trackId={} stationId={} totalItems={}",
                jobId, target.getParameter(), trackId, stationId, totalItems);
        metrics.recordAccepted(JobType.MASTER_DATA_REPUBLISH);

        return MasterDataRepublishJobSubmission.accepted(job);
    }

    /** Trabajo por identificador. */
    public AsyncJob getJob(UUID jobId) {
        return store.findById(jobId)
                .orElseThrow(() -> new NotFoundException("No existe el trabajo " + jobId));
    }

    /**
     * Comprueba los parametros y devuelve la seleccion.
     *
     * <p>Se juntan TODOS los problemas en una sola {@code ValidationException} en vez de salir por
     * el primero: quien lanza esto a mano con curl agradece enterarse de las dos cosas que tiene
     * mal de una vez. Viaja con el mismo cuerpo Problem Details que el resto de la API.</p>
     */
    private MasterDataRepublishTarget validate(String entity, Long trackId, Long stationId) {
        List<Alert> errors = new ArrayList<>();

        MasterDataRepublishTarget target = MasterDataRepublishTarget.fromParameter(entity);

        if (target == null) {
            errors.add(Alert.ofDanger(
                    "La entidad a republicar es obligatoria y debe ser una de: %s"
                            .formatted(MasterDataRepublishTarget.admittedValues()), "entity"));
            throw new ValidationException(errors);
        }

        // Un filtro que solo aplicase a un tercio de lo pedido es una trampa: con all se republican
        // los tres tipos enteros o no se usa all.
        if (target == MasterDataRepublishTarget.ALL && (trackId != null || stationId != null)) {
            errors.add(Alert.ofDanger(
                    "Con entity=all no se admiten filtros: republica los tres tipos enteros",
                    "entity"));
        }

        if (trackId != null && target != MasterDataRepublishTarget.PROFILE) {
            errors.add(Alert.ofDanger(
                    "El filtro por via solo aplica a los perfiles", "trackId"));
        }

        if (stationId != null && (target == MasterDataRepublishTarget.PROFILE)) {
            errors.add(Alert.ofDanger(
                    "El filtro por estacion solo aplica a seccionadores y aisladores de seccion",
                    "stationId"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        return target;
    }

    /**
     * Cuenta la seleccion antes de crear la fila del trabajo.
     *
     * <p>A diferencia del tope de las cargas masivas, este llega a tiempo: no hay nada
     * deserializado en memoria todavia. Sirve para dos cosas —rechazar con 400 lo que no se va a
     * hacer, y que el 202 lleve ya su {@code totalItems}— por el precio de un {@code count}.</p>
     */
    private int countItems(MasterDataRepublishTarget target, Long trackId, Long stationId) {
        long total = 0;

        for (MasterDataRepublishTarget each : target.expand()) {
            total += switch (each) {
                case PROFILE -> profileRepository.countForRepublish(trackId);
                case DISCONNECTOR -> disconnectorRepository.countForRepublish(stationId);
                case SECTION_INSULATOR -> sectionInsulatorRepository.countForRepublish(stationId);
                case ALL -> 0L;
            };
        }

        // Un trabajo que nace y muere COMPLETED sin haber hecho nada es un 202 por una peticion que
        // casi siempre es un error del cliente: una via o una estacion que no existe, o vacia.
        if (total == 0) {
            throw new ValidationException(List.of(Alert.ofDanger(
                    "La seleccion no contiene ningun elemento que republicar", "entity")));
        }

        int maxItems = properties.getRepublish().getMaxItems();

        if (total > maxItems) {
            throw new ValidationException(List.of(Alert.ofDanger(
                    "El republicado admite como maximo %d elementos y la seleccion tiene %d"
                            .formatted(maxItems, total), "entity")));
        }

        return (int) total;
    }

    /**
     * Ejecucion en el hilo de fondo. Aqui no puede escaparse nada: una excepcion que saliera moriria
     * dentro del executor sin traza y dejaria el trabajo en RUNNING para siempre, indistinguible de
     * uno que sigue vivo.
     */
    private void run(UUID jobId,
                     MasterDataRepublishTarget target,
                     Long trackId,
                     Long stationId,
                     int totalItems) {
        long startedAtNanos = System.nanoTime();

        // Fuera del try a proposito: si algo global revienta, el catch todavia tiene los contadores
        // y los errores por elemento ya recogidos, que es cuando mas falta hacen.
        // Con el total ya contado, y no a null: cada volcado copia los contadores del progreso
        // sobre la fila —totalItems incluido—, asi que un null aqui borraria el total que el 202
        // acaba de anunciar y dejaria el seguimiento sin denominador.
        ProfileJobProgress progress = new ProfileJobProgress(
                totalItems, properties.getProfile(), p -> store.saveProgress(jobId, p));

        try {
            store.markRunning(jobId);

            runner.run(jobId, target, trackId, stationId, progress);

            JobStatus finalStatus = progress.getFailedItems() > 0
                    ? JobStatus.COMPLETED_WITH_ERRORS
                    : JobStatus.COMPLETED;

            store.markFinished(jobId, finalStatus, progress, null);
            metrics.recordFinished(JobType.MASTER_DATA_REPUBLISH, finalStatus, elapsedSince(startedAtNanos));
        } catch (Exception e) {
            log.error("Fallo el republicado jobId={} entity={}", jobId, target.getParameter(), e);
            store.markFinished(jobId, JobStatus.FAILED, progress, messageOf(e));
            metrics.recordFinished(JobType.MASTER_DATA_REPUBLISH, JobStatus.FAILED, elapsedSince(startedAtNanos));
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
