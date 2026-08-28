package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.repository.jpa.jobs.AsyncJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El latido es lo que permite que el tope de concurrencia sea del despliegue entero sin que un
 * proceso muerto se quede un hueco para siempre. Lo que se fija aqui es que solo late lo que esta
 * en curso, que sea una sola consulta y que un fallo no tumbe el planificador.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncJobHeartbeatTest {

    @Mock
    private AsyncJobRepository repository;

    @InjectMocks
    private AsyncJobHeartbeat heartbeat;

    @Test
    @DisplayName("sin trabajos en curso no consulta la base de datos")
    void sinTrabajosNoConsulta() {
        heartbeat.beat();

        // Una replica ociosa no debe generar una consulta cada quince segundos para siempre.
        verify(repository, never()).heartbeat(anyCollection(), anyCollection(), any());
    }

    @Test
    @DisplayName("late por todos los trabajos de la replica en una sola consulta")
    void unaSolaConsultaParaTodos() {
        UUID uno = UUID.randomUUID();
        UUID dos = UUID.randomUUID();

        heartbeat.register(uno);
        heartbeat.register(dos);
        heartbeat.beat();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(repository).heartbeat(ids.capture(), any(), any(Instant.class));

        // Un UPDATE por pasada y no uno por trabajo: el coste no crece con la carga.
        assertThat(ids.getValue()).containsExactlyInAnyOrder(uno, dos);
        assertThat(heartbeat.inFlightCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("un trabajo dado de baja deja de latir")
    void elTrabajoTerminadoDejaDeLatir() {
        UUID jobId = UUID.randomUUID();

        heartbeat.register(jobId);
        heartbeat.unregister(jobId);
        heartbeat.beat();

        verify(repository, never()).heartbeat(anyCollection(), anyCollection(), any());
        assertThat(heartbeat.inFlightCount()).isZero();
    }

    @Test
    @DisplayName("un fallo escribiendo el latido no tumba el planificador")
    void elFalloNoPropaga() {
        when(repository.heartbeat(anyCollection(), anyCollection(), any()))
                .thenThrow(new IllegalStateException("base de datos no disponible"));

        heartbeat.register(UUID.randomUUID());

        // Si se propagara, el planificador dejaria de ejecutar TAMBIEN la purga y el reaper. Y el
        // comportamiento resultante es el correcto: un trabajo que no puede escribir tampoco esta
        // avanzando, asi que su latido se enfria y acabara dandose por muerto.
        assertThatCode(heartbeat::beat).doesNotThrowAnyException();
    }
}
