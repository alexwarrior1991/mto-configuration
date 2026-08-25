package com.alejandro.mtoconfiguration.core.outbox;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OutboxDispatchTriggerTest {

    /** Ejecuta en el mismo hilo, para poder razonar sobre el orden. */
    private static final java.util.concurrent.Executor DIRECTO = Runnable::run;

    @Test
    void unaPeticionProvocaUnaPasadaDelRelay() {
        AtomicInteger pasadas = new AtomicInteger();

        new OutboxDispatchTrigger(DIRECTO, pasadas::incrementAndGet).requestDispatch();

        assertThat(pasadas).hasValue(1);
    }

    @Test
    void milPeticionesAntesDeEmpezarSeAgrupanEnUnaSolaPasada() throws Exception {
        // Un bulkCreate de mil entidades escribe mil mensajes y pide mil despertares:
        // ejecutarlos todos seria mil pasadas del relay para el mismo trabajo.
        AtomicInteger pasadas = new AtomicInteger();
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        OutboxDispatchTrigger trigger = new OutboxDispatchTrigger(executor, () -> {
            try {
                arranca.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            pasadas.incrementAndGet();
        });

        for (int i = 0; i < 1000; i++) {
            trigger.requestDispatch();
        }
        arranca.countDown();

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(pasadas)
                .as("mil peticiones antes de que arranque la pasada son una sola pasada")
                .hasValue(1);
    }

    @Test
    void loQueSeGuardaMientrasElRelayTrabajaProvocaOtraPasada() {
        // El indicador se libera ANTES de ejecutar justamente por esto: si se liberara
        // al terminar, un mensaje escrito durante la pasada se quedaria esperando al
        // siguiente sondeo, que es la latencia que se intenta quitar.
        AtomicInteger pasadas = new AtomicInteger();
        OutboxDispatchTrigger[] referencia = new OutboxDispatchTrigger[1];

        referencia[0] = new OutboxDispatchTrigger(DIRECTO, () -> {
            if (pasadas.incrementAndGet() == 1) {
                referencia[0].requestDispatch();
            }
        });

        referencia[0].requestDispatch();

        assertThat(pasadas).hasValue(2);
    }

    @Test
    void unFalloPublicandoNoEscapaHaciaElHiloQueLoPidio() {
        // Quien pide el despertar es la transaccion de negocio que acaba de confirmar:
        // un error del relay no puede llegarle de vuelta.
        OutboxDispatchTrigger trigger = new OutboxDispatchTrigger(DIRECTO, () -> {
            throw new IllegalStateException("broker caido");
        });

        assertThatCode(trigger::requestDispatch).doesNotThrowAnyException();
    }

    @Test
    void siElEjecutorRechazaLaTareaElPlanificadorSigueSiendoLaRed() {
        OutboxDispatchTrigger trigger = new OutboxDispatchTrigger(
                runnable -> {
                    throw new RejectedExecutionException("saturado");
                },
                () -> { });

        assertThatCode(trigger::requestDispatch).doesNotThrowAnyException();
    }

    @Test
    void trasUnRechazoSeVuelveAAdmitirLaSiguientePeticion() {
        // Si el indicador se quedara puesto, un rechazo puntual dejaria el despertar
        // inmediato inutilizado para siempre.
        AtomicInteger pasadas = new AtomicInteger();
        boolean[] rechaza = {true};

        OutboxDispatchTrigger trigger = new OutboxDispatchTrigger(
                runnable -> {
                    if (rechaza[0]) {
                        throw new RejectedExecutionException("saturado");
                    }
                    runnable.run();
                },
                pasadas::incrementAndGet);

        trigger.requestDispatch();
        rechaza[0] = false;
        trigger.requestDispatch();

        assertThat(pasadas).hasValue(1);
    }
}
