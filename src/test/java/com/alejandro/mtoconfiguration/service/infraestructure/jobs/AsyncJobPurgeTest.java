package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Purga de trabajos viejos.
 *
 * <p>Sin ella {@code async_job} solo crece y el directorio de exportacion acumula CSV para siempre.
 * De los dos, el disco es el que da problemas antes: una exportacion de una via grande son megas y
 * nadie los borraba.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncJobPurgeTest {

    @Mock
    private AsyncJobRepository repository;
    @Mock
    private ProfileJobFiles files;

    private AsyncJobPurgeService purgeService;
    private AsyncJobProperties properties;

    @BeforeEach
    void setUp() {
        purgeService = new AsyncJobPurgeService(repository, files);

        properties = new AsyncJobProperties();
        properties.getPurge().setRetention(Duration.ofDays(7));
        properties.getPurge().setBatchSize(2);
        properties.getPurge().setMaxBatchesPerRun(10);
    }

    private AsyncJobRepository.PurgeCandidate candidate(String fileName) {
        UUID id = UUID.randomUUID();

        return new AsyncJobRepository.PurgeCandidate() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getFileName() {
                return fileName;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.now().minus(Duration.ofDays(30));
            }
        };
    }

    @Test
    @DisplayName("borra el fichero ANTES que la fila")
    void elFicheroVaPrimero() {
        when(repository.findByStatusNotInAndCreatedAtBeforeOrderByCreatedAt(anyCollection(), any(), any()))
                .thenReturn(List.of(candidate("profiles-track-1-abc.csv")));

        assertThat(purgeService.purgeBatch(Instant.now(), 2)).isEqualTo(1);

        // Al reves, un corte entre las dos operaciones dejaria un CSV que ya no referencia nadie y
        // que ninguna pasada volveria a mirar. Asi el peor caso es un fichero borrado cuya fila
        // sigue ahi, que la siguiente pasada recoge sin inmutarse.
        var order = inOrder(files, repository);
        order.verify(files).delete("profiles-track-1-abc.csv");
        order.verify(repository).deleteAllByIdInBatch(anyCollection());
    }

    @Test
    @DisplayName("un trabajo sin fichero se borra igual")
    void trabajoSinFichero() {
        when(repository.findByStatusNotInAndCreatedAtBeforeOrderByCreatedAt(anyCollection(), any(), any()))
                .thenReturn(List.of(candidate(null)));

        assertThat(purgeService.purgeBatch(Instant.now(), 2)).isEqualTo(1);

        verify(files, never()).delete(anyString());
        verify(repository).deleteAllByIdInBatch(anyCollection());
    }

    @Test
    @DisplayName("sin candidatos no se borra nada")
    void sinCandidatos() {
        when(repository.findByStatusNotInAndCreatedAtBeforeOrderByCreatedAt(anyCollection(), any(), any()))
                .thenReturn(List.of());

        assertThat(purgeService.purgeBatch(Instant.now(), 2)).isZero();
        verify(repository, never()).deleteAllByIdInBatch(anyCollection());
    }

    @Test
    @DisplayName("el planificador para en cuanto un lote sale incompleto")
    void paraAlVaciarse() {
        AsyncJobPurgeService service = org.mockito.Mockito.mock(AsyncJobPurgeService.class);
        when(service.purgeBatch(any(), anyInt())).thenReturn(2, 2, 1);

        new AsyncJobPurgeScheduler(service, files, properties).purgeOldJobs();

        // Un lote incompleto significa que ya no queda nada por debajo del umbral: seguir seria
        // una consulta por vuelta hasta agotar max-batches-per-run.
        verify(service, times(3)).purgeBatch(any(), anyInt());
    }

    @Test
    @DisplayName("el umbral es ahora menos la retencion, y barre tambien los huerfanos")
    void umbralYHuerfanos() {
        AsyncJobPurgeService service = org.mockito.Mockito.mock(AsyncJobPurgeService.class);
        when(service.purgeBatch(any(), anyInt())).thenReturn(0);

        new AsyncJobPurgeScheduler(service, files, properties).purgeOldJobs();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        verify(files).deleteOrphansOlderThan(threshold.capture());

        assertThat(threshold.getValue()).isBefore(Instant.now().minus(Duration.ofDays(6)));
    }

    @Test
    @DisplayName("un fallo de purga no tumba el planificador")
    void elFalloNoPropaga() {
        AsyncJobPurgeService service = org.mockito.Mockito.mock(AsyncJobPurgeService.class);
        when(service.purgeBatch(any(), anyInt())).thenThrow(new IllegalStateException("sin conexion"));

        // El mismo planificador mueve el latido: propagar aqui pararia tambien aquello.
        assertThatCode(() -> new AsyncJobPurgeScheduler(service, files, properties).purgeOldJobs())
                .doesNotThrowAnyException();
    }
}
