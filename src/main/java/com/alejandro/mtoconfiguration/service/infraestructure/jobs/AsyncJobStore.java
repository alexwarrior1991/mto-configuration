package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.enums.jobs.JobSlotGroup;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura del estado de un trabajo, cada operacion en su propia transaccion.
 *
 * <p>Este es el punto donde se decide la propiedad mas importante de toda la capa: <b>el estado del
 * trabajo se confirma aparte del trabajo</b>. Cada metodo va en {@code REQUIRES_NEW}, de modo que
 * marcar RUNNING, refrescar el progreso y cerrar el trabajo son commits independientes de las
 * transacciones que crean o modifican perfiles.</p>
 *
 * <p>Si el estado compartiera transaccion con el trabajo, el resultado seria justo el contrario del
 * que se busca: nadie veria progreso hasta el final —porque nada estaria confirmado— y un fallo al
 * cerrar el trabajo desharia por rollback los perfiles ya escritos. Peor aun en el caso de FAILED:
 * el intento de dejar constancia del fallo se iria abajo con la transaccion que fallo, y el trabajo
 * quedaria eternamente en RUNNING sin que nadie supiera por que.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobStore {

    private final AsyncJobRepository repository;
    private final ObjectMapper objectMapper;
    private final AsyncJobProperties properties;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Reserva cupo y crea el trabajo, todo en la misma transaccion.
     *
     * <p>Devuelve el trabajo en PENDING si habia hueco y en REJECTED si no. En los dos casos hay
     * fila: un rechazo que no deja rastro solo se puede investigar leyendo logs.</p>
     *
     * <h2>Por que un cerrojo y no un simple recuento</h2>
     *
     * <p>«Cuenta los que hay y, si caben, mete uno» es una condicion de carrera de manual: dos
     * peticiones simultaneas leen el mismo recuento, las dos ven hueco y las dos entran. Con un
     * tope de uno, eso son dos cargas masivas a la vez, que es exactamente lo que el tope existia
     * para impedir.</p>
     *
     * <p>{@code pg_advisory_xact_lock} serializa esa secuencia sin necesidad de una fila que
     * bloquear —el cupo es un concepto, no un registro— y se suelta solo al confirmar la
     * transaccion, de modo que no hay ninguna ruta de error que lo deje cogido. Cada grupo tiene su
     * clave, asi que pedir hueco de exportacion no espera por uno de carga masiva.</p>
     *
     * <h2>Por que ya no hay semaforo</h2>
     *
     * <p>Antes el tope lo ponia un {@code Semaphore} en memoria. Un semaforo por JVM significa un
     * tope por replica: con tres replicas, «maximo 2 exportaciones» eran seis contra la misma base
     * de datos. Contra la tabla el limite es del despliegue entero, y de paso desaparece toda una
     * clase de fallos —el permiso que no se devuelve y reduce el tope en silencio— porque el hueco
     * no lo suelta nadie: se deja de ocupar cuando el trabajo termina o cuando deja de latir.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AsyncJob createClaimingSlot(JobType type,
                                       Long trackId,
                                       String mapperType,
                                       Integer totalItems,
                                       String createdBy) {
        JobSlotGroup group = type.getSlotGroup();
        int maxConcurrency = maxConcurrencyOf(group);

        lockSlotGroup(group);

        Instant aliveSince = Instant.now().minus(properties.getHeartbeat().getTimeout());
        long alive = repository.countAlive(group.types(), AsyncJobRepository.ACTIVE_STATUSES, aliveSince);

        if (alive >= maxConcurrency) {
            log.warn("Sin cupo para un trabajo [{}]: {} de {} en marcha", group, alive, maxConcurrency);

            return persist(type, JobStatus.REJECTED, trackId, mapperType, totalItems, createdBy,
                    "Sin capacidad para ejecutar mas trabajos de tipo %s (%d de %d en marcha)"
                            .formatted(type, alive, maxConcurrency));
        }

        return persist(type, JobStatus.PENDING, trackId, mapperType, totalItems, createdBy, null);
    }

    /**
     * Cerrojo consultivo del grupo, hasta el fin de la transaccion.
     *
     * <p>Se ejecuta por {@code EntityManager} y no como metodo del repositorio porque
     * {@code pg_advisory_xact_lock} devuelve {@code void} en PostgreSQL, un tipo que Spring Data no
     * sabe mapear a un valor de retorno.</p>
     */
    private void lockSlotGroup(JobSlotGroup group) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", group.getLockKey())
                .getSingleResult();
    }

    /** Trabajos de ese grupo que siguen latiendo, en todo el despliegue. Para las metricas. */
    @Transactional(readOnly = true)
    public long countAlive(JobSlotGroup group) {
        Instant aliveSince = Instant.now().minus(properties.getHeartbeat().getTimeout());
        return repository.countAlive(group.types(), AsyncJobRepository.ACTIVE_STATUSES, aliveSince);
    }

    public int maxConcurrencyOf(JobSlotGroup group) {
        AsyncJobProperties.ProfileJobs profileJobs = properties.getProfile();

        int configured = switch (group) {
            case EXPORT -> profileJobs.getExportMaxConcurrency();
            case BULK -> profileJobs.getBulkMaxConcurrency();
        };

        // Un tope de cero dejaria la funcionalidad muerta sin que el arranque dijera nada.
        return Math.max(1, configured);
    }

    /**
     * Crea el trabajo ya con su identificador y lo confirma.
     *
     * <p>Tiene que estar confirmado <b>antes</b> de que responda el 202: el cliente se lleva un
     * {@code Location} y lo siguiente que hara es consultarlo. Un 404 en la primera consulta del
     * identificador que acabamos de darle no tiene ninguna explicacion aceptable.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AsyncJob create(JobType type,
                           JobStatus status,
                           Long trackId,
                           String mapperType,
                           Integer totalItems,
                           String createdBy,
                           String errorMessage) {
        return persist(type, status, trackId, mapperType, totalItems, createdBy, errorMessage);
    }

    private AsyncJob persist(JobType type,
                             JobStatus status,
                             Long trackId,
                             String mapperType,
                             Integer totalItems,
                             String createdBy,
                             String errorMessage) {
        AsyncJob job = new AsyncJob();
        Instant now = Instant.now();

        job.setId(UUID.randomUUID());
        job.setType(type);
        job.setStatus(status);
        job.setCreatedAt(now);
        // Nace latiendo: entre que se confirma la fila y arranca el hilo de fondo pasan
        // milisegundos, pero un trabajo sin latido inicial seria un trabajo que ya nace muerto para
        // el reparto de cupo.
        job.setHeartbeatAt(now);
        job.setTrackId(trackId);
        job.setMapperType(mapperType);
        job.setTotalItems(totalItems);
        job.setCreatedBy(createdBy);
        job.setErrorMessage(errorMessage);

        // Un trabajo que nace rechazado nace tambien terminado: no habra un despues que rellene
        // esta fecha, y dejarla vacia lo haria parecer eternamente en curso.
        if (status.isTerminal()) {
            job.setFinishedAt(now);
        }

        AsyncJob saved = repository.saveAndFlush(job);
        log.info("Trabajo creado jobId={} type={} status={} createdBy={}",
                saved.getId(), type, status, createdBy);

        return saved;
    }

    /** Marca el arranque efectivo en el hilo de fondo. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID jobId) {
        repository.findById(jobId).ifPresent(job -> {
            Instant now = Instant.now();

            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(now);
            job.setHeartbeatAt(now);
            repository.saveAndFlush(job);

            log.info("Trabajo en ejecucion jobId={} type={} status={}",
                    jobId, job.getType(), JobStatus.RUNNING);
        });
    }

    /** Refresca los contadores de un trabajo en curso, sin tocar su estado. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProgress(UUID jobId, ProfileJobProgress progress) {
        repository.findById(jobId).ifPresent(job -> {
            applyCounters(job, progress);
            repository.saveAndFlush(job);

            log.debug("Progreso jobId={} procesados={} ok={} ko={}",
                    jobId, progress.getProcessedItems(), progress.getSuccessfulItems(),
                    progress.getFailedItems());
        });
    }

    /**
     * Cierra el trabajo en un estado terminal.
     *
     * <p>Recibe el progreso porque el ultimo tramo de elementos puede no haberse volcado todavia:
     * el volcado periodico solo salta cada cierto tiempo, y sin este cierre el trabajo terminaria
     * mostrando menos elementos procesados de los que realmente hizo. Llega tambien en los cierres
     * por fallo, para no tirar lo ya procesado ni los errores ya diagnosticados.</p>
     *
     * <p>Admite {@code null} solo en el caso en que no llego a ejecutarse nada —un trabajo que no
     * se pudo ni encolar—, donde no hay contadores que conservar.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFinished(UUID jobId,
                             JobStatus status,
                             ProfileJobProgress progress,
                             String errorMessage) {
        repository.findById(jobId).ifPresent(job -> {
            if (progress != null) {
                applyCounters(job, progress);
                job.setFileName(progress.getOutputFileName());
                job.setErrorDetailsJson(serializeItemErrors(jobId, progress.getItemErrors()));
            }

            job.setStatus(status);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(truncateErrorMessage(errorMessage));

            repository.saveAndFlush(job);

            log.info("Trabajo terminado jobId={} type={} status={} procesados={} ok={} ko={}",
                    jobId, job.getType(), status, job.getProcessedItems(),
                    job.getSuccessfulItems(), job.getFailedItems());
        });
    }

    @Transactional(readOnly = true)
    public Optional<AsyncJob> findById(UUID jobId) {
        return repository.findById(jobId);
    }

    /** Errores por elemento de un trabajo, tal y como se guardaron. Lista vacia si no hay. */
    public List<JobItemErrorDTO> readItemErrors(AsyncJob job) {
        String json = job.getErrorDetailsJson();

        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            return List.of(objectMapper.readValue(json, JobItemErrorDTO[].class));
        } catch (JsonProcessingException e) {
            // El detalle es informativo: si no se puede leer, se pierde el detalle, no la
            // consulta del estado.
            log.warn("No se pudo leer el detalle de errores del trabajo jobId={}", job.getId(), e);
            return List.of();
        }
    }

    private void applyCounters(AsyncJob job, ProfileJobProgress progress) {
        job.setTotalItems(progress.getTotalItems());
        job.setProcessedItems(progress.getProcessedItems());
        job.setSuccessfulItems(progress.getSuccessfulItems());
        job.setFailedItems(progress.getFailedItems());
    }

    private String serializeItemErrors(UUID jobId, List<JobItemErrorDTO> itemErrors) {
        if (itemErrors.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(itemErrors);
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar el detalle de errores del trabajo jobId={}", jobId, e);
            return null;
        }
    }

    /**
     * La columna admite 1000 caracteres y un stack trace encadenado los pasa de largo. Se recorta
     * aqui y no se confia en la base de datos: PostgreSQL no trunca, rechaza el INSERT entero, y el
     * trabajo se quedaria sin poder registrar por que fallo.
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= 1000) {
            return errorMessage;
        }

        return errorMessage.substring(0, 999) + "…";
    }
}
