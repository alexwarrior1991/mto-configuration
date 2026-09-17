package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.jobs.AsyncJob;
import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.enums.jobs.MasterDataRepublishTarget;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orquestacion del republicado, de punta a punta y sin esperas.
 *
 * <p>El executor ejecuta en el hilo que llama, igual que en {@code ProfileJobServiceTest}: no es un
 * atajo para no dormir, es lo que permite comprobar el resultado de forma determinista en vez de
 * sondear un estado que puede o no haber llegado.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MasterDataRepublishJobServiceTest {

    @Mock
    private AsyncJobStore store;
    @Mock
    private AsyncJobHeartbeat heartbeat;
    @Mock
    private MasterDataRepublishJobRunner runner;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DisconnectorRepository disconnectorRepository;
    @Mock
    private SectionInsulatorRepository sectionInsulatorRepository;

    private AsyncJobProperties properties;
    private MasterDataRepublishJobService service;

    @BeforeEach
    void setUp() {
        properties = new AsyncJobProperties();

        when(currentUserService.getUsername()).thenReturn(Optional.of("ana"));

        when(profileRepository.countForRepublish(any())).thenReturn(3L);
        when(disconnectorRepository.countForRepublish(any())).thenReturn(2L);
        when(sectionInsulatorRepository.countForRepublish(any())).thenReturn(1L);

        // El reparto de cupo vive en la base de datos, asi que aqui se simula su respuesta: por
        // defecto hay hueco. La atomicidad de esa reserva se prueba contra PostgreSQL de verdad en
        // AsyncJobSlotIT.
        when(store.createClaimingSlot(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> job(JobStatus.PENDING));

        service = new MasterDataRepublishJobService(store, heartbeat,
                new AsyncJobMetrics(new SimpleMeterRegistry()), runner, currentUserService,
                properties, new TaskExecutorAdapter(Runnable::run),
                profileRepository, disconnectorRepository, sectionInsulatorRepository);
    }

    private AsyncJob job(JobStatus status) {
        AsyncJob job = new AsyncJob();
        job.setId(UUID.randomUUID());
        job.setType(JobType.MASTER_DATA_REPUBLISH);
        job.setStatus(status);
        job.setCreatedAt(Instant.now());
        return job;
    }

    @Test
    @DisplayName("un republicado aceptado corre y termina COMPLETED")
    void republicadoCompletado() {
        MasterDataRepublishJobSubmission submission = service.submit("profile", 7L, null);

        assertThat(submission.accepted()).isTrue();

        // El trackId si va a su columna —significa exactamente eso—; mapperType no, que aqui no
        // significa nada.
        verify(store).createClaimingSlot(eq(JobType.MASTER_DATA_REPUBLISH), eq(7L), isNull(),
                eq(3), eq("ana"));
        verify(store).markRunning(submission.job().getId());
        verify(runner).run(eq(submission.job().getId()), eq(MasterDataRepublishTarget.PROFILE),
                eq(7L), isNull(), any());
        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.COMPLETED), any(), isNull());
        verify(heartbeat).unregister(submission.job().getId());
    }

    @Test
    @DisplayName("el total anunciado en el 202 es la suma de los tres recuentos con all")
    void totalDeAll() {
        service.submit("all", null, null);

        verify(store).createClaimingSlot(any(), isNull(), isNull(), eq(6), any());
    }

    @Test
    @DisplayName("el progreso arranca con el total ya contado, no a null")
    void elProgresoConservaElTotal() {
        MasterDataRepublishJobSubmission submission = service.submit("profile", null, null);

        ArgumentCaptor<ProfileJobProgress> progress = ArgumentCaptor.forClass(ProfileJobProgress.class);
        verify(store).markFinished(eq(submission.job().getId()), any(), progress.capture(), isNull());

        // Cada volcado copia los contadores del progreso sobre la fila, totalItems incluido: con un
        // null aqui, el primer volcado borraria el total que el 202 acaba de anunciar.
        assertThat(progress.getValue().getTotalItems()).isEqualTo(3);
    }

    @Test
    @DisplayName("con elementos fallidos el trabajo termina COMPLETED_WITH_ERRORS")
    void falloParcial() {
        doAnswer(invocation -> {
            ProfileJobProgress progress = invocation.getArgument(4);
            progress.itemSucceeded();
            progress.itemFailed(1, "profile", "NotFound", "El elemento 2 ya no existe");
            return null;
        }).when(runner).run(any(), any(), any(), any(), any());

        MasterDataRepublishJobSubmission submission = service.submit("profile", null, null);

        verify(store).markFinished(eq(submission.job().getId()),
                eq(JobStatus.COMPLETED_WITH_ERRORS), any(), isNull());
    }

    @Test
    @DisplayName("un fallo global deja el trabajo FAILED con su motivo")
    void falloGlobal() {
        doThrow(new IllegalStateException("outbox caido"))
                .when(runner).run(any(), any(), any(), any(), any());

        MasterDataRepublishJobSubmission submission = service.submit("profile", null, null);

        verify(store).markFinished(eq(submission.job().getId()), eq(JobStatus.FAILED), any(),
                eq("outbox caido"));
        verify(heartbeat).unregister(submission.job().getId());
    }

    @Test
    @DisplayName("sin cupo se devuelve rechazo y el trabajo no llega a encolarse")
    void sinCupo() {
        when(store.createClaimingSlot(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> job(JobStatus.REJECTED));

        MasterDataRepublishJobSubmission submission = service.submit("profile", null, null);

        assertThat(submission.accepted()).isFalse();
        assertThat(submission.job().getStatus()).isEqualTo(JobStatus.REJECTED);

        // La fila existe —el rechazo es observable— pero nada arranca: ni latido ni recorrido.
        verify(heartbeat, never()).register(any());
        verify(runner, never()).run(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("una entidad desconocida o ausente sale como 400 con los valores admitidos")
    void entidadInvalida() {
        assertThatThrownBy(() -> service.submit("perfiles", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("section-insulator");

        assertThatThrownBy(() -> service.submit(null, null, null))
                .isInstanceOf(ValidationException.class);

        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("el filtro por via solo vale para perfiles y el de estacion no vale para perfiles")
    void filtrosCruzados() {
        assertThatThrownBy(() -> service.submit("disconnector", 7L, null))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> service.submit("profile", null, 4L))
                .isInstanceOf(ValidationException.class);

        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("all no admite filtros: un filtro que solo aplica a un tercio de lo pedido es una trampa")
    void allNoAdmiteFiltros() {
        assertThatThrownBy(() -> service.submit("all", 7L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("all");

        assertThatThrownBy(() -> service.submit("all", null, 4L))
                .isInstanceOf(ValidationException.class);

        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("una seleccion vacia no crea un trabajo que nace y muere sin hacer nada")
    void seleccionVacia() {
        when(profileRepository.countForRepublish(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.submit("profile", 99L, null))
                .isInstanceOf(ValidationException.class);

        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("el tope de elementos se comprueba con el recuento, antes de crear la fila")
    void topeDeElementos() {
        properties.getRepublish().setMaxItems(2);

        assertThatThrownBy(() -> service.submit("profile", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2");

        verify(store, never()).createClaimingSlot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("consultar un trabajo que no existe es un 404")
    void trabajoInexistente() {
        UUID unknown = UUID.randomUUID();
        when(store.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(unknown)).isInstanceOf(NotFoundException.class);
    }
}
