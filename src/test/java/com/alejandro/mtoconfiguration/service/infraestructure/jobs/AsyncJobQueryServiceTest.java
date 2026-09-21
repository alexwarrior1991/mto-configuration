package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.AsyncJobListItem;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El listado de trabajos: que filtra solo por lo que viene, que el orden pasa por la lista blanca
 * y que la fila resume el trabajo sin arrastrar los errores por elemento.
 */
@ExtendWith(MockitoExtension.class)
class AsyncJobQueryServiceTest {

    private static final UUID JOB_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private AsyncJobRepository repository;

    @InjectMocks
    private AsyncJobQueryService service;

    private static AsyncJob job() {
        AsyncJob job = new AsyncJob();
        job.setId(JOB_ID);
        job.setType(JobType.PROFILE_IMPORT);
        job.setStatus(JobStatus.COMPLETED_WITH_ERRORS);
        job.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        job.setStartedAt(Instant.parse("2026-01-01T10:00:01Z"));
        job.setFinishedAt(Instant.parse("2026-01-01T10:02:00Z"));
        job.setTotalItems(100);
        job.setProcessedItems(100);
        job.setSuccessfulItems(97);
        job.setFailedItems(3);
        job.setErrorMessage(null);
        job.setErrorDetailsJson("[{\"index\":1}]");
        return job;
    }

    @Test
    @DisplayName("sin filtros no hay condiciones y el orden es el mas reciente primero")
    void sinFiltrosNoHayCondiciones() {
        when(repository.findAll(any(Predicate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job())));

        Page<AsyncJobListItem> page = service.list(null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(predicate.capture(), pageable.capture());
        assertThat(((BooleanBuilder) predicate.getValue()).hasValue()).isFalse();
        assertThat(pageable.getValue().getSort()).isEqualTo(AsyncJobQueryService.DEFAULT_SORT);
        assertThat(page.getContent()).singleElement().extracting(AsyncJobListItem::id).isEqualTo(JOB_ID);
    }

    @Test
    @DisplayName("tipo y estado filtran cuando vienen")
    void tipoYEstadoFiltran() {
        when(repository.findAll(any(Predicate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(JobType.PROFILE_EXPORT, JobStatus.COMPLETED, PageRequest.of(0, 20));

        ArgumentCaptor<Predicate> predicate = ArgumentCaptor.forClass(Predicate.class);
        verify(repository).findAll(predicate.capture(), any(Pageable.class));
        assertThat(predicate.getValue().toString())
                .contains("asyncJob.type = PROFILE_EXPORT")
                .contains("asyncJob.status = COMPLETED");
    }

    @Test
    @DisplayName("un orden fuera de la lista blanca cae al de defecto; uno permitido se respeta")
    void elOrdenPasaPorLaListaBlanca() {
        Pageable porUnaColumnaSinIndice = AsyncJobQueryService.safe(
                PageRequest.of(1, 5, Sort.by("errorDetailsJson")));
        assertThat(porUnaColumnaSinIndice.getPageNumber()).isEqualTo(1);
        assertThat(porUnaColumnaSinIndice.getPageSize()).isEqualTo(5);
        assertThat(porUnaColumnaSinIndice.getSort()).isEqualTo(AsyncJobQueryService.DEFAULT_SORT);

        Sort permitido = Sort.by(Sort.Direction.ASC, "status").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(AsyncJobQueryService.safe(PageRequest.of(0, 5, permitido)).getSort()).isEqualTo(permitido);

        Sort mezclado = Sort.by("status").and(Sort.by("fileName"));
        assertThat(AsyncJobQueryService.safe(PageRequest.of(0, 5, mezclado)).getSort())
                .isEqualTo(AsyncJobQueryService.DEFAULT_SORT);
    }

    @Test
    @DisplayName("la fila resume el trabajo: contadores y mensaje, nunca los errores por elemento")
    void laFilaResumeElTrabajo() {
        AsyncJob job = job();
        job.setErrorMessage("se paro a medias");

        AsyncJobListItem item = AsyncJobListItem.of(job);

        assertThat(item.id()).isEqualTo(JOB_ID);
        assertThat(item.type()).isEqualTo(JobType.PROFILE_IMPORT);
        assertThat(item.status()).isEqualTo(JobStatus.COMPLETED_WITH_ERRORS);
        assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(item.finishedAt()).isEqualTo(Instant.parse("2026-01-01T10:02:00Z"));
        assertThat(item.totalItems()).isEqualTo(100);
        assertThat(item.successfulItems()).isEqualTo(97);
        assertThat(item.failedItems()).isEqualTo(3);
        assertThat(item.error()).isEqualTo("se paro a medias");
        assertThat(item.trackId()).isNull();
    }
}
