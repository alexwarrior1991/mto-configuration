package com.alejandro.mtoconfiguration.repository.jpa.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Acceso a los trabajos en segundo plano.
 *
 * <p>Deliberadamente pequeno: el ciclo de vida de un trabajo lo gobierna
 * {@code AsyncJobStore}, que es quien pone los limites transaccionales. Aqui solo viven las
 * consultas.</p>
 */
public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {

    /**
     * Trabajos en un estado dado anteriores a un instante.
     *
     * <p>Existe para explotacion y para la purga: los PENDING/RUNNING viejos son trabajos que se
     * quedaron a medias porque murio el proceso que los ejecutaba, y los terminales antiguos son
     * los candidatos a borrar. Va servida por el indice parcial {@code idx_async_job_active}.</p>
     */
    Collection<AsyncJob> findByStatusInAndCreatedAtBefore(Collection<JobStatus> statuses, Instant before);
}
