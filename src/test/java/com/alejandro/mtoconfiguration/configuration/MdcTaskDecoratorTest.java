package com.alejandro.mtoconfiguration.configuration;

import com.alejandro.mtoconfiguration.configuration.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El MDC es un {@code ThreadLocal}, como el {@code SecurityContext}: no cruza al hilo virtual por si
 * solo, y la perdida es silenciosa. Sin esto, lo que corre en {@code /async} y en los trabajos
 * escribiria sus logs y sus eventos de outbox sin el {@code correlationId} de la peticion.
 */
class MdcTaskDecoratorTest {

    private static final String CORRELATION_ID = "8c3b8c1a-1111-4222-8333-444444444444";

    private final AsyncTaskExecutor executor = new AsyncConfiguration().applicationTaskExecutor();

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    @DisplayName("el executor de la aplicacion lleva el correlationId al hilo de trabajo")
    void elExecutorLlevaElMdcAlHiloDeTrabajo() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);

        Future<String> enElHilo = executor.submit(() -> MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(enElHilo.get(5, TimeUnit.SECONDS)).isEqualTo(CORRELATION_ID);
    }

    @Test
    @DisplayName("sin el decorador el MDC se pierde: es la regresion que se vigila")
    void sinDecoradorElMdcSePierde() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);

        AsyncTaskExecutor sinDecorador = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        Future<String> enElHilo = sinDecorador.submit(() -> MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(enElHilo.get(5, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("una tarea encolada sin MDC no hereda el de nadie, y el hilo queda limpio al terminar")
    void unaTareaSinMdcNoHeredaNada() throws Exception {
        MDC.clear();

        Future<String> enElHilo = executor.submit(() -> MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(enElHilo.get(5, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("el decorador deja el hilo como estaba: lo que ponga la tarea no se queda para la siguiente")
    void dejaElHiloComoEstaba() {
        Runnable tarea = new MdcTaskDecorator().decorate(() -> MDC.put(CorrelationIdFilter.MDC_KEY, "de-la-tarea"));

        MDC.put(CorrelationIdFilter.MDC_KEY, "del-hilo");
        tarea.run();

        // El decorador capturo un MDC vacio al crearse, lo puso al arrancar y devolvio el del hilo
        // al terminar: ni lo que capturo ni lo que puso la tarea sobreviven.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("del-hilo");
    }
}
