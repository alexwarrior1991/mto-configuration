package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orquestacion de un trabajo, de punta a punta y sin esperas.
 *
 * <p>El executor de la prueba ejecuta en el hilo que llama. No es un atajo para evitar
 * {@code Thread.sleep}: es lo que permite comprobar el <b>resultado</b> del trabajo de forma
 * determinista en vez de sondear un estado que puede o no haber llegado. Que en produccion el
 * executor sea otro —hilos virtuales con propagacion del SecurityContext— es justamente el motivo
 * de que se inyecte.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileJobServiceTest {

    @Mock
    private AsyncJobStore store;
    @Mock
    private ProfileExportJobRunner exportRunner;
    @Mock
    private ProfileBulkJobRunner bulkRunner;
    @Mock
    private ProfileExportService exportService;
    @Mock
    private CurrentUserService currentUserService;

    private ProfileJobConcurrencyGuard guard;
    private AsyncJobProperties properties;
    private ProfileJobService service;

    @BeforeEach
    void setUp() {
        properties = new AsyncJobProperties();
        properties.getProfile().setExportMaxConcurrency(1);
        properties.getProfile().setBulkMaxConcurrency(1);

        guard = new ProfileJobConcurrencyGuard(properties);

        when(currentUserService.getUsername()).thenReturn(Optional.of("ana"));
        when(exportService.resolveMapperName(anyString())).thenAnswer(i -> i.getArgument(0));
        when(store.create(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> job(invocation.getArgument(0), invocation.getArgument(1)));

        service = new ProfileJobService(store, guard, exportRunner, bulkRunner, exportService,
                currentUserService, properties, new TaskExecutorAdapter(Runnable::run));
    }

    private AsyncJob job(JobType type, JobStatus status) {
        AsyncJob job = new AsyncJob();
        job.setId(UUID.randomUUID());
        job.setType(type);
        job.setStatus(status);
        job.setCreatedAt(Instant.now());
        return job;
    }

    @Test
    @DisplayName("una exportacion aceptada corre y termina COMPLETED")
    void exportacionCompletada() {
        ProfileJobSubmission submission = service.submitExport(42L, "basic");

        assertThat(submission.accepted()).isTrue();
        verify(store).create(eq(JobType.PROFILE_EXPORT), eq(JobStatus.PENDING), eq(42L),
                eq("basic"), isNull(), eq("ana"), isNull());
        verify(store).markRunning(submission.job().getId());
        verify(exportRunner).run(eq(submission.job().getId()), eq(42L), eq("basic"), any());
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.COMPLETED), any(), isNull());
    }

    @Test
    @DisplayName("con elementos fallidos el trabajo termina COMPLETED_WITH_ERRORS")
    void cargaConFallosParciales() {
        doAnswer(invocation -> {
            ProfileJobProgress progress = invocation.getArgument(3);
            progress.itemSucceeded();
            progress.itemFailed(1, "create", "ValidationException", "kp obligatorio");
            return null;
        }).when(bulkRunner).run(any(), eq(JobType.PROFILE_BULK_CREATE), any(), any());

        ProfileJobSubmission submission = service.submitBulkCreate(List.of(new ProfileDTO(), new ProfileDTO()));

        ArgumentCaptor<ProfileJobProgress> progress = ArgumentCaptor.forClass(ProfileJobProgress.class);
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.COMPLETED_WITH_ERRORS),
                progress.capture(), isNull());

        // Exito parcial y no fallo: los elementos correctos estan escritos y confirmados.
        assertThat(progress.getValue().getSuccessfulItems()).isEqualTo(1);
        assertThat(progress.getValue().getFailedItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("un fallo global deja el trabajo FAILED con su motivo")
    void falloGlobal() {
        doThrow(new UncheckedIOException("disco lleno", new IOException("no space left")))
                .when(exportRunner).run(any(), any(), any(), any());

        ProfileJobSubmission submission = service.submitExport(7L, "basic");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.FAILED), isNull(), message.capture());
        assertThat(message.getValue()).contains("UncheckedIOException").contains("disco lleno");
    }

    @Test
    @DisplayName("el permiso vuelve tanto si el trabajo acaba bien como si falla")
    void elPermisoSiempreVuelve() {
        service.submitExport(1L, "basic");
        assertThat(guard.availablePermits(JobType.PROFILE_EXPORT)).isEqualTo(1);

        doThrow(new IllegalStateException("boom")).when(exportRunner).run(any(), any(), any(), any());
        service.submitExport(2L, "basic");

        // Un permiso no devuelto reduce el tope de forma permanente, y el sintoma —los trabajos se
        // rechazan sin motivo aparente— aparece mucho despues de la causa.
        assertThat(guard.availablePermits(JobType.PROFILE_EXPORT)).isEqualTo(1);
    }

    @Test
    @DisplayName("sin capacidad el trabajo nace REJECTED y no se ejecuta")
    void rechazoPorCapacidad() {
        // Se agota el unico permiso y se deja retenido, como haria un trabajo todavia en curso.
        guard.tryAcquire(JobType.PROFILE_EXPORT).orElseThrow();

        ProfileJobSubmission submission = service.submitExport(9L, "basic");

        assertThat(submission.accepted()).isFalse();
        verify(store).create(eq(JobType.PROFILE_EXPORT), eq(JobStatus.REJECTED), eq(9L), eq("basic"),
                isNull(), eq("ana"), anyString());

        // El rechazo queda persistido para que sea observable, pero no se hace ningun trabajo.
        verify(exportRunner, never()).run(any(), any(), any(), any());
        verify(store, never()).markRunning(any());
    }

    @Test
    @DisplayName("un lote vacio no llega a crear trabajo")
    void loteVacio() {
        assertThatThrownBy(() -> service.submitBulkCreate(List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("vacia");

        // Ni fila, ni permiso consumido: la peticion se queda en la puerta.
        verify(store, never()).create(any(), any(), any(), any(), any(), any(), any());
        assertThat(guard.availablePermits(JobType.PROFILE_BULK_CREATE)).isEqualTo(1);
    }

    @Test
    @DisplayName("consultar un trabajo inexistente da 404 por la via del manejador global")
    void trabajoInexistente() {
        UUID unknown = UUID.randomUUID();
        when(store.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(unknown))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }
}
