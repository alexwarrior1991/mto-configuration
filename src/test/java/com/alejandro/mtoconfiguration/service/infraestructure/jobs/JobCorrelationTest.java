package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El {@code jobId} como {@code correlationId} del hilo de fondo es lo que agrupa cada evento de una
 * importacion bajo el trabajo. Si el envoltorio dejara de ponerlo, los eventos saldrian con el de la
 * peticion de un segundo que lo lanzo, o sin ninguno, y nadie lo veria hasta mirar la bandeja de
 * notificaciones.
 */
class JobCorrelationTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    @AfterEach
    void limpiar() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    @DisplayName("mientras corre la tarea, el correlationId del MDC es el jobId")
    void duranteLaTareaElCorrelationIdEsElJobId() {
        AtomicReference<String> visto = new AtomicReference<>();

        JobCorrelation.wrap(JOB_ID, () -> visto.set(MDC.get(CorrelationIdFilter.MDC_KEY))).run();

        assertThat(visto.get()).isEqualTo(JOB_ID.toString());
    }

    @Test
    @DisplayName("al terminar no deja nada en un hilo que no tenia nada")
    void alTerminarNoDejaNada() {
        JobCorrelation.wrap(JOB_ID, () -> { }).run();

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("al terminar devuelve el que hubiera antes: el de la peticion que el executor copio al hilo")
    void alTerminarDevuelveElAnterior() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "peticion-que-lanzo-el-trabajo");

        JobCorrelation.wrap(JOB_ID, () -> { }).run();

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("peticion-que-lanzo-el-trabajo");
    }

    @Test
    @DisplayName("tambien lo devuelve si la tarea revienta")
    void loDevuelveAunqueLaTareaReviente() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "anterior");
        Runnable tarea = JobCorrelation.wrap(JOB_ID, () -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(tarea::run).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("anterior");
    }
}
