package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.configuration.web.CorrelationIdFilter;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Hace del {@code jobId} el {@code correlationId} del hilo que ejecuta un trabajo.
 *
 * <p>Una peticion HTTP lleva el suyo, puesto por {@code CorrelationIdFilter}, y lo que escribe en el
 * outbox sale con el. Un trabajo en segundo plano no es una peticion: dura minutos, escribe miles
 * de eventos y lo que los une es el trabajo, no la llamada de un segundo que lo lanzo. Con el
 * {@code jobId} en el MDC, cada linea de log del trabajo y cada evento que escribe (los de datos
 * maestros de una importacion, y su propio {@code job.finished}) llevan el mismo identificador,
 * que es lo que permite a {@code mto-notification} contar una importacion entera como una sola
 * cosa en vez de como doce mil.</p>
 *
 * <p>Se pone al encolar, envolviendo la tarea, para que cubra todo lo que hace el hilo de fondo sin
 * tocar el cuerpo de cada trabajo; y se quita al terminar, dejando lo que hubiera antes, porque los
 * hilos del executor no son de nadie.</p>
 */
final class JobCorrelation {

    private JobCorrelation() {
    }

    /** La tarea, con el {@code jobId} como {@code correlationId} mientras corre. */
    static Runnable wrap(UUID jobId, Runnable task) {
        String correlationId = jobId.toString();

        return () -> {
            String previous = MDC.get(CorrelationIdFilter.MDC_KEY);
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

            try {
                task.run();
            } finally {
                if (previous == null) {
                    MDC.remove(CorrelationIdFilter.MDC_KEY);
                } else {
                    MDC.put(CorrelationIdFilter.MDC_KEY, previous);
                }
            }
        };
    }
}
