package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.QAsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.AsyncJobListItem;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * El listado de trabajos de todas las familias, del mas reciente al mas antiguo.
 *
 * <p>Hasta ahora un trabajo solo se podia consultar por id, asi que quien lo lanzaba era el unico
 * que podia volver a verlo, y solo mientras recordase el identificador. La tabla ya guarda todos
 * los trabajos hasta la purga; esto solo la lee.</p>
 *
 * <p>El orden va contra una lista blanca, como en la busqueda por criterios: un campo que no
 * este cae al orden por defecto en vez de dar error, y asi un cliente no puede provocar una
 * consulta por una columna sin indice.</p>
 */
@Service
@RequiredArgsConstructor
public class AsyncJobQueryService {

    static final Set<String> SORTABLE = Set.of("createdAt", "startedAt", "finishedAt", "status", "type");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AsyncJobRepository repository;

    /** Trabajos por tipo y estado; un filtro ausente no filtra. */
    public Page<AsyncJobListItem> list(JobType type, JobStatus status, Pageable pageable) {
        QAsyncJob job = QAsyncJob.asyncJob;
        BooleanBuilder builder = new BooleanBuilder();
        if (type != null) {
            builder.and(job.type.eq(type));
        }
        if (status != null) {
            builder.and(job.status.eq(status));
        }
        return repository.findAll(builder, safe(pageable)).map(AsyncJobListItem::of);
    }

    static Pageable safe(Pageable pageable) {
        Sort sort = pageable.getSort();
        boolean allowed = sort.isSorted() && sort.stream().allMatch(order -> SORTABLE.contains(order.getProperty()));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), allowed ? sort : DEFAULT_SORT);
    }
}
