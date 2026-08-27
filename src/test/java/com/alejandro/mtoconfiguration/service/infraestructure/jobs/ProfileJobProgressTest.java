package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los contadores de un trabajo son lo unico que ve quien sondea su estado. Aqui se fija cada cuanto
 * bajan a la base de datos y, sobre todo, que la lista de errores tenga techo: sin el, una carga
 * masiva mal formada convierte la fila del trabajo y la respuesta del endpoint en varios megas.
 */
class ProfileJobProgressTest {

    private AsyncJobProperties.ProfileJobs settings(Duration flushInterval, int maxErrors, int maxLength) {
        AsyncJobProperties.ProfileJobs settings = new AsyncJobProperties().getProfile();
        settings.setProgressFlushInterval(flushInterval);
        settings.setMaxItemErrors(maxErrors);
        settings.setMaxItemErrorMessageLength(maxLength);
        return settings;
    }

    /** Intervalo tan largo que ningun volcado salta por tiempo durante la prueba. */
    private static final Duration NUNCA = Duration.ofHours(1);

    /**
     * Reloj manejado a mano.
     *
     * <p>Es lo que permite probar una cadencia por tiempo sin dormir el hilo: la prueba avanza el
     * reloj cuando quiere y el resultado es determinista, en lugar de depender de que la maquina
     * tarde lo que se espera.</p>
     */
    private static final class FakeClock implements LongSupplier {

        private long nanos;

        void advance(Duration amount) {
            nanos += amount.toNanos();
        }

        @Override
        public long getAsLong() {
            return nanos;
        }
    }

    @Test
    @DisplayName("cuenta aciertos y fallos por separado")
    void cuentaAciertosYFallos() {
        ProfileJobProgress progress = new ProfileJobProgress(3, settings(NUNCA, 50, 500), p -> { });

        progress.itemSucceeded();
        progress.itemFailed(1, "create", "ValidationException", "kp obligatorio");
        progress.itemSucceeded();

        assertThat(progress.getTotalItems()).isEqualTo(3);
        assertThat(progress.getProcessedItems()).isEqualTo(3);
        assertThat(progress.getSuccessfulItems()).isEqualTo(2);
        assertThat(progress.getFailedItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("vuelca el progreso por tiempo, no por numero de elementos")
    void vuelcaPorTiempo() {
        AtomicInteger flushes = new AtomicInteger();
        FakeClock clock = new FakeClock();
        ProfileJobProgress progress = new ProfileJobProgress(0, settings(Duration.ofSeconds(2), 50, 500),
                p -> flushes.incrementAndGet(), clock);

        // Cien mil elementos instantaneos, como las lineas de un CSV: ni un solo volcado. Con la
        // cadencia por recuento que habia antes esto eran cuatro mil escrituras.
        for (int i = 0; i < 100_000; i++) {
            progress.itemSucceeded();
        }
        assertThat(flushes.get()).isZero();

        clock.advance(Duration.ofSeconds(2));
        progress.itemSucceeded();
        assertThat(flushes.get()).isEqualTo(1);

        // Y el reloj se reinicia en cada volcado: el siguiente no llega hasta pasado el intervalo.
        progress.itemSucceeded();
        assertThat(flushes.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(5));
        progress.itemSucceeded();
        assertThat(flushes.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("con elementos lentos vuelca en cada uno, sin esperar a acumular N")
    void elementosLentos() {
        AtomicInteger flushes = new AtomicInteger();
        FakeClock clock = new FakeClock();
        ProfileJobProgress progress = new ProfileJobProgress(3, settings(Duration.ofSeconds(2), 50, 500),
                p -> flushes.incrementAndGet(), clock);

        // Tres elementos de diez segundos cada uno. Por recuento habrian tenido que llegar a 25
        // antes de enseñar nada: el trabajo habria parecido parado durante cuatro minutos.
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofSeconds(10));
            progress.itemSucceeded();
        }

        assertThat(flushes.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("guarda solo los primeros errores, pero los sigue contando todos")
    void acotaLosErroresGuardados() {
        ProfileJobProgress progress = new ProfileJobProgress(1_000, settings(NUNCA, 3, 500), p -> { });

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
        ProfileJobProgress progress = new ProfileJobProgress(1, settings(NUNCA, 10, 20), p -> { });

        progress.itemFailed(0, "update", "GenericException", "x".repeat(500));

        String message = progress.getItemErrors().getFirst().message();
        assertThat(message).hasSize(21).startsWith("x".repeat(20)).endsWith("…");
    }

    @Test
    @DisplayName("un fallo escribiendo el progreso no tumba el trabajo")
    void elFalloAlVolcarNoPropaga() {
        List<Integer> seen = new ArrayList<>();
        FakeClock clock = new FakeClock();
        ProfileJobProgress progress = new ProfileJobProgress(2, settings(Duration.ofSeconds(1), 10, 500), p -> {
            seen.add(p.getProcessedItems());
            throw new IllegalStateException("base de datos no disponible");
        }, clock);

        // El trabajo real ya esta hecho; perder un contador es una molestia, perder por eso una
        // carga de diez mil elementos ya procesados, no.
        clock.advance(Duration.ofSeconds(1));
        progress.itemSucceeded();
        clock.advance(Duration.ofSeconds(1));
        progress.itemSucceeded();

        assertThat(seen).containsExactly(1, 2);
        assertThat(progress.getSuccessfulItems()).isEqualTo(2);
    }
}
