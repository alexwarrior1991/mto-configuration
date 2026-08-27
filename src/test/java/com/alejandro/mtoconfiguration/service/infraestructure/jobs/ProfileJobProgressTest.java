package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los contadores de un trabajo son lo unico que ve quien sondea su estado. Aqui se fija cada cuanto
 * bajan a la base de datos y, sobre todo, que la lista de errores tenga techo: sin el, una carga
 * masiva mal formada convierte la fila del trabajo y la respuesta del endpoint en varios megas.
 */
class ProfileJobProgressTest {

    private AsyncJobProperties.ProfileJobs settings(int flushInterval, int maxErrors, int maxLength) {
        AsyncJobProperties.ProfileJobs settings = new AsyncJobProperties().getProfile();
        settings.setProgressFlushInterval(flushInterval);
        settings.setMaxItemErrors(maxErrors);
        settings.setMaxItemErrorMessageLength(maxLength);
        return settings;
    }

    @Test
    @DisplayName("cuenta aciertos y fallos por separado")
    void cuentaAciertosYFallos() {
        ProfileJobProgress progress = new ProfileJobProgress(3, settings(100, 50, 500), p -> { });

        progress.itemSucceeded();
        progress.itemFailed(1, "create", "ValidationException", "kp obligatorio");
        progress.itemSucceeded();

        assertThat(progress.getTotalItems()).isEqualTo(3);
        assertThat(progress.getProcessedItems()).isEqualTo(3);
        assertThat(progress.getSuccessfulItems()).isEqualTo(2);
        assertThat(progress.getFailedItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("vuelca el progreso cada N elementos, no en cada uno")
    void vuelcaCadaNElementos() {
        AtomicInteger flushes = new AtomicInteger();
        ProfileJobProgress progress = new ProfileJobProgress(10, settings(5, 50, 500),
                p -> flushes.incrementAndGet());

        for (int i = 0; i < 4; i++) {
            progress.itemSucceeded();
        }

        assertThat(flushes.get())
                .as("por debajo del intervalo no se escribe: un UPDATE por elemento duplicaria las escrituras de la carga")
                .isZero();

        progress.itemSucceeded();
        assertThat(flushes.get()).isEqualTo(1);

        for (int i = 0; i < 5; i++) {
            progress.itemSucceeded();
        }
        assertThat(flushes.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("guarda solo los primeros errores, pero los sigue contando todos")
    void acotaLosErroresGuardados() {
        ProfileJobProgress progress = new ProfileJobProgress(1_000, settings(1_000, 3, 500), p -> { });

        for (int i = 0; i < 200; i++) {
            progress.itemFailed(i, "create", "ValidationException", "fallo " + i);
        }

        assertThat(progress.getFailedItems())
                .as("el recuento no se acota: es lo que le dice al cliente cuanto ha fallado de verdad")
                .isEqualTo(200);

        List<JobItemErrorDTO> errors = progress.getItemErrors();
        assertThat(errors).hasSize(3);
        assertThat(errors).extracting(JobItemErrorDTO::index).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("recorta los mensajes largos")
    void recortaMensajes() {
        ProfileJobProgress progress = new ProfileJobProgress(1, settings(1_000, 10, 20), p -> { });

        progress.itemFailed(0, "update", "GenericException", "x".repeat(500));

        String message = progress.getItemErrors().getFirst().message();
        assertThat(message).hasSize(21).startsWith("x".repeat(20)).endsWith("…");
    }

    @Test
    @DisplayName("un fallo escribiendo el progreso no tumba el trabajo")
    void elFalloAlVolcarNoPropaga() {
        List<Integer> seen = new ArrayList<>();
        ProfileJobProgress progress = new ProfileJobProgress(2, settings(1, 10, 500), p -> {
            seen.add(p.getProcessedItems());
            throw new IllegalStateException("base de datos no disponible");
        });

        // El trabajo real ya esta hecho; perder un contador es una molestia, perder por eso una
        // carga de diez mil elementos ya procesados, no.
        progress.itemSucceeded();
        progress.itemSucceeded();

        assertThat(seen).containsExactly(1, 2);
        assertThat(progress.getSuccessfulItems()).isEqualTo(2);
    }
}
