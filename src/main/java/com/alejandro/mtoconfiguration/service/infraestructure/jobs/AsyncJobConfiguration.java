package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cableado de la capa de trabajos en segundo plano.
 *
 * <p>No declara ningun executor: los trabajos corren en el {@code applicationTaskExecutor} de
 * {@code AsyncConfiguration}, que ya usa hilos virtuales y propaga el {@code SecurityContext}. Un
 * executor propio aqui habria creado un segundo pool invisible para la configuracion y, sobre todo,
 * habria perdido la identidad del usuario en el salto de hilo.</p>
 *
 * <p>{@code @EnableScheduling} va aqui aunque ya este en {@code OutboxConfiguration}: aquella es
 * condicional a {@code app.outbox.enabled}, de modo que apagar el outbox habria dejado sin
 * planificador al latido, al reaper y a la purga de los trabajos —en silencio, que es lo peor—.
 * Declararlo dos veces es inocuo: Spring registra un unico post-procesador.</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AsyncJobProperties.class)
public class AsyncJobConfiguration {

    @Bean
    public AsyncJobMetrics asyncJobMetrics(MeterRegistry meterRegistry) {
        return new AsyncJobMetrics(meterRegistry);
    }
}
