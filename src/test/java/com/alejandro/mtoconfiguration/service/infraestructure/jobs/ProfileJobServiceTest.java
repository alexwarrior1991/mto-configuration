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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private AsyncJobHeartbeat heartbeat;
    @Mock
    private ProfileExportJobRunner exportRunner;
    @Mock
    private ProfileBulkJobRunner bulkRunner;
    @Mock
    private ProfileExportService exportService;
    @Mock
    private CurrentUserService currentUserService;

    private AsyncJobProperties properties;
    private AsyncJobMetrics metrics;
    private SimpleMeterRegistry meterRegistry;
    private ProfileJobService service;

    @BeforeEach
    void setUp() {
        properties = new AsyncJobProperties();
        properties.getProfile().setExportMaxConcurrency(1);
        properties.getProfile().setBulkMaxConcurrency(1);

        meterRegistry = new SimpleMeterRegistry();
        metrics = new AsyncJobMetrics(meterRegistry);

        when(currentUserService.getUsername()).thenReturn(Optional.of("ana"));
        when(exportService.resolveMapperName(anyString())).thenAnswer(i -> i.getArgument(0));

        // El reparto de cupo vive en la base de datos, asi que aqui se simula su respuesta: por
        // defecto hay hueco. La atomicidad de esa reserva se prueba contra PostgreSQL de verdad en
        // AsyncJobSlotIT; falsearla con un doble no probaria nada de lo que importa.
        when(store.createClaimingSlot(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> job(invocation.getArgument(0), JobStatus.PENDING));

        service = new ProfileJobService(store, heartbeat, metrics, exportRunner, bulkRunner,
                exportService, currentUserService, properties, new TaskExecutorAdapter(Runnable::run));
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
        verify(store).createClaimingSlot(eq(JobType.PROFILE_EXPORT), eq(42L), eq("basic"),
                isNull(), eq("ana"));
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
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.FAILED), any(), message.capture());
        assertThat(message.getValue()).contains("UncheckedIOException").contains("disco lleno");
    }

    @Test
    @DisplayName("un fallo global conserva lo procesado y los errores ya diagnosticados")
    void elFalloGlobalNoTiraElDiagnostico() {
        doAnswer(invocation -> {
            ProfileJobProgress progress = invocation.getArgument(3);
            progress.itemSucceeded();
            progress.itemFailed(1, "create", "ValidationException", "kp obligatorio");
            progress.itemSucceeded();
            throw new IllegalStateException("conexion perdida");
        }).when(bulkRunner).run(any(), eq(JobType.PROFILE_BULK_CREATE), any(), any());

        ProfileJobSubmission submission = service.submitBulkCreate(
                List.of(new ProfileDTO(), new ProfileDTO(), new ProfileDTO()));

        ArgumentCaptor<ProfileJobProgress> progress = ArgumentCaptor.forClass(ProfileJobProgress.class);
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.FAILED),
                progress.capture(), anyString());

        // Un fallo global no invalida los elementos ya escritos ni los errores ya diagnosticados:
        // son lo unico que dice por donde iba el trabajo cuando se cayo.
        assertThat(progress.getValue()).isNotNull();
        assertThat(progress.getValue().getProcessedItems()).isEqualTo(3);
        assertThat(progress.getValue().getSuccessfulItems()).isEqualTo(2);
        assertThat(progress.getValue().getItemErrors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("kp obligatorio"));
    }

    @Test
    @DisplayName("una exportacion fallida no deja fichero que ofrecer")
    void exportacionFallidaSinFichero() {
        doAnswer(invocation -> {
            ProfileJobProgress progress = invocation.getArgument(3);
            progress.itemSucceeded();
            throw new UncheckedIOException("disco lleno", new IOException("no space left"));
        }).when(exportRunner).run(any(), any(), any(), any());

        ProfileJobSubmission submission = service.submitExport(7L, "basic");

        ArgumentCaptor<ProfileJobProgress> progress = ArgumentCaptor.forClass(ProfileJobProgress.class);
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.FAILED),
                progress.capture(), anyString());

        // El nombre solo se fija cuando el CSV esta completo, asi que un volcado a medias nunca
        // llega a ofrecerse para descarga aunque ahora el progreso viaje en el cierre.
        assertThat(progress.getValue().getOutputFileName()).isNull();
        assertThat(progress.getValue().getProcessedItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("el latido se registra al aceptar y se suelta acabe como acabe el trabajo")
    void elLatidoSiempreSeSuelta() {
        ProfileJobSubmission ok = service.submitExport(1L, "basic");
        verify(heartbeat).register(ok.job().getId());
        verify(heartbeat).unregister(ok.job().getId());

        doThrow(new IllegalStateException("boom")).when(exportRunner).run(any(), any(), any(), any());
        ProfileJobSubmission ko = service.submitExport(2L, "basic");

        // Olvidarlo dejaria a la replica refrescando el latido de un trabajo que termino hace rato,
        // y ese trabajo fantasma seguiria contando contra el cupo del despliegue.
        verify(heartbeat).unregister(ko.job().getId());
    }

    @Test
    @DisplayName("sin cupo el trabajo nace REJECTED y no se ejecuta ni late")
    void rechazoPorCapacidad() {
        when(store.createClaimingSlot(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> job(invocation.getArgument(0), JobStatus.REJECTED));

        ProfileJobSubmission submission = service.submitExport(9L, "basic");

        assertThat(submission.accepted()).isFalse();

        // El rechazo queda persistido para que sea observable, pero no se hace ningun trabajo.
        verify(exportRunner, never()).run(any(), any(), any(), any());
        verify(store, never()).markRunning(any());
        verify(heartbeat, never()).register(any());
    }

    @Test
    @DisplayName("aceptaciones y rechazos quedan contados por separado")
    void metricasDeAdmision() {
        service.submitExport(1L, "basic");

        when(store.createClaimingSlot(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> job(invocation.getArgument(0), JobStatus.REJECTED));
        service.submitExport(2L, "basic");

        // Sin esta metrica, quedarse sin cupo es invisible: la aplicacion responde 429, deja su
        // fila y nadie se entera hasta que alguien se queja de que no le deja exportar.
        assertThat(counter("accepted")).isEqualTo(1);
        assertThat(counter("rejected")).isEqualTo(1);
    }

    @Test
    @DisplayName("un lote mas grande que el tope se rechaza antes de crear nada")
    void loteDemasiadoGrande() {
        properties.getProfile().setMaxBulkItems(2);

        assertThatThrownBy(() -> service.submitBulkCreate(
                List.of(new ProfileDTO(), new ProfileDTO(), new ProfileDTO())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximo 2");

        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
    }

    private double counter(String outcome) {
        return meterRegistry.get(AsyncJobMetrics.SUBMITTED_COUNT)
                .tag("type", JobType.PROFILE_EXPORT.name())
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    @Test
    @DisplayName("un lote vacio no llega a crear trabajo")
    void loteVacio() {
        assertThatThrownBy(() -> service.submitBulkCreate(List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("vacia");

        // Ni fila, ni cupo consumido: la peticion se queda en la puerta.
        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
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
