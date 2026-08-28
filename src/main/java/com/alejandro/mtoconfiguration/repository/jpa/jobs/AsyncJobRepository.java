package com.alejandro.mtoconfiguration.repository.jpa.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a los trabajos en segundo plano.
 *
 * <p>Todas las consultas de aqui giran alrededor de la misma idea: <b>un trabajo cuenta mientras
 * late</b>. El estado por si solo no sirve para repartir cupo, porque una fila en RUNNING de una
 * replica que murio seguiria ocupando sitio para siempre.</p>
 */
public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {

    /** Estados en los que un trabajo sigue vivo y, por tanto, puede ocupar cupo. */
    Collection<JobStatus> ACTIVE_STATUSES = List.of(JobStatus.PENDING, JobStatus.RUNNING);

    /**
     * Trabajos de un grupo que siguen dando señales de vida.
     *
     * <p>Es el recuento que decide si hay hueco. El filtro por latido es lo que hace que un proceso
     * muerto no retenga su sitio: deja de latir y, pasado el plazo, deja de contar sin que nadie
     * tenga que ir a limpiarlo. Va servida por el indice parcial {@code idx_async_job_slot}.</p>
     */
    @Query("""
            select count(job) from AsyncJob job
            where job.type in :types
              and job.status in :activeStatuses
              and job.heartbeatAt > :aliveSince
            """)
    long countAlive(@Param("types") Collection<JobType> types,
                    @Param("activeStatuses") Collection<JobStatus> activeStatuses,
                    @Param("aliveSince") Instant aliveSince);

    /**
     * Refresca de una sola vez el latido de los trabajos que ejecuta esta replica.
     *
     * <p>Un UPDATE por replica y pasada, no uno por trabajo: el coste no crece con el numero de
     * trabajos en curso. El filtro por estado evita revivir el latido de uno que ya cerro.</p>
     */
    @Modifying
    @Query("""
            update AsyncJob job
               set job.heartbeatAt = :now
             where job.id in :ids
               and job.status in :activeStatuses
            """)
    int heartbeat(@Param("ids") Collection<UUID> ids,
                  @Param("activeStatuses") Collection<JobStatus> activeStatuses,
                  @Param("now") Instant now);

    /**
     * Cierra como fallidos los trabajos que dejaron de latir.
     *
     * <p>No corre prisa: el hueco ya quedo libre en cuanto el latido se enfrio, porque
     * {@link #countAlive} solo mira a los vivos. Esto corrige el <b>estado</b> que se le enseña a
     * quien consulta, para que un trabajo difunto no aparezca eternamente como RUNNING.</p>
     */
    @Modifying
    @Query("""
            update AsyncJob job
               set job.status = :failed,
                   job.finishedAt = :now,
                   job.errorMessage = :message
             where job.status in :activeStatuses
               and job.heartbeatAt < :deadSince
            """)
    int failStale(@Param("activeStatuses") Collection<JobStatus> activeStatuses,
                  @Param("failed") JobStatus failed,
                  @Param("deadSince") Instant deadSince,
                  @Param("now") Instant now,
                  @Param("message") String message);

    /**
     * Candidatos a purga: trabajos ya terminados y anteriores al umbral de retencion.
     *
     * <p>Proyeccion y no la entidad a proposito: solo hacen falta el identificador y el nombre del
     * fichero, y cargar las filas enteras traeria tambien el JSON de errores —hasta decenas de
     * kilobytes por trabajo— para tirarlo acto seguido.</p>
     */
    List<PurgeCandidate> findByStatusNotInAndCreatedAtBeforeOrderByCreatedAt(
            Collection<JobStatus> statuses, Instant createdBefore, Limit limit);

    /** Lo minimo para borrar un trabajo: su fila y, si lo hubo, su fichero. */
    interface PurgeCandidate {

        UUID getId();

        String getFileName();

        Instant getCreatedAt();
    }
}
