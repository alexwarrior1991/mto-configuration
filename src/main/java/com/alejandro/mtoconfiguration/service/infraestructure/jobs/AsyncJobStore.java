package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        AsyncJob job = new AsyncJob();
        Instant now = Instant.now();

        job.setId(UUID.randomUUID());
        job.setType(type);
        job.setStatus(status);
        job.setCreatedAt(now);
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
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(Instant.now());
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
     * el volcado periodico solo salta cada N, y sin este cierre el trabajo terminaria mostrando
     * menos elementos procesados de los que realmente hizo.</p>
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
