package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.enums.jobs.JobStatus;
import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Cierre de los trabajos que dejaron de dar señales.
 *
 * <p>Lo que hace segura esta pasada con varias replicas es que el criterio no sea «esta en RUNNING»
 * sino «lleva un rato sin latir»: la version ingenua —marcar RUNNING como fallidos al arrancar—
 * habria matado los trabajos vivos de las demas replicas.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncJobReaperTest {

    @Mock
    private AsyncJobRepository repository;

    private AsyncJobProperties properties;
    private AsyncJobMetrics metrics;
    private SimpleMeterRegistry meterRegistry;
    private AsyncJobReaper reaper;

    @BeforeEach
    void setUp() {
        properties = new AsyncJobProperties();
        properties.getHeartbeat().setTimeout(Duration.ofMinutes(2));

        meterRegistry = new SimpleMeterRegistry();
        metrics = new AsyncJobMetrics(meterRegistry);
        reaper = new AsyncJobReaper(repository, properties, metrics);
    }

    @Test
    @DisplayName("solo cierra lo que lleva mas del plazo sin latir")
    void soloCierraLoQueNoLate() {
        when(repository.failStale(anyCollection(), any(), any(), any(), anyString())).thenReturn(2);

        Instant antes = Instant.now();
        reaper.failStaleJobs();

        ArgumentCaptor<Instant> deadSince = ArgumentCaptor.forClass(Instant.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<JobStatus>> statuses = ArgumentCaptor.forClass(Collection.class);

        verify(repository).failStale(statuses.capture(), eq(JobStatus.FAILED), deadSince.capture(),
                any(Instant.class), eq(AsyncJobReaper.STALE_MESSAGE));

        assertThat(statuses.getValue()).containsExactlyInAnyOrder(JobStatus.PENDING, JobStatus.RUNNING);
        assertThat(deadSince.getValue())
                .as("el umbral es ahora menos el plazo de silencio, no un instante cualquiera")
                .isBetween(antes.minus(Duration.ofMinutes(2)).minusSeconds(5),
                        Instant.now().minus(Duration.ofMinutes(2)).plusSeconds(5));
    }

    @Test
    @DisplayName("cuenta los trabajos cerrados para que el sintoma sea visible")
    void cuentaLosCerrados() {
        when(repository.failStale(anyCollection(), any(), any(), any(), anyString())).thenReturn(3);

        reaper.failStaleJobs();

        // Que una replica se muera a mitad de un trabajo se recupera solo, pero no debe pasar
        // inadvertido: si este contador sube de forma sostenida, algo va mal en el despliegue.
        assertThat(meterRegistry.get(AsyncJobMetrics.REAPED_COUNT).counter().count()).isEqualTo(3);
    }

    @Test
    @DisplayName("sin trabajos difuntos no cuenta nada")
    void sinDifuntosNoCuentaNada() {
        when(repository.failStale(anyCollection(), any(), any(), any(), anyString())).thenReturn(0);

        reaper.failStaleJobs();

        assertThat(meterRegistry.find(AsyncJobMetrics.REAPED_COUNT).counter().count()).isZero();
    }

    @Test
    @DisplayName("un fallo no tumba el planificador")
    void elFalloNoPropaga() {
        when(repository.failStale(anyCollection(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("base de datos no disponible"));

        assertThatCode(reaper::failStaleJobs).doesNotThrowAnyException();
    }
}
