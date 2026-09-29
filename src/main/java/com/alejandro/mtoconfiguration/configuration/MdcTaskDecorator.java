package com.alejandro.mtoconfiguration.configuration;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Lleva el MDC del hilo que encola al hilo que ejecuta.
 *
 * <p>El MDC es un {@code ThreadLocal}, igual que el {@code SecurityContext}: un hilo virtual recien
 * creado arranca sin el. Sin esta copia, lo que corre en {@code /async} y en los trabajos en segundo
 * plano escribe sus lineas de log y sus eventos de outbox sin el {@code correlationId} de la
 * peticion que lo pidio, y la traza que el gateway empezo con esa cabecera se corta justo en el
 * salto de hilo.</p>
 *
 * <p>Al terminar deja el hilo como estaba. Con hilos virtuales de un solo uso da igual, pero el
 * decorador no sabe sobre que executor corre y un hilo reutilizado con un MDC ajeno atribuiria las
 * lineas de una peticion a otra.</p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            replace(context);

            try {
                runnable.run();
            } finally {
                replace(previous);
            }
        };
    }

    private static void replace(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
