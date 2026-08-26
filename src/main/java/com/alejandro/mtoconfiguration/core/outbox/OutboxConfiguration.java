package com.alejandro.mtoconfiguration.core.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    public OutboxRetryPolicy outboxRetryPolicy(OutboxProperties outboxProperties) {
        return new OutboxRetryPolicy(outboxProperties);
    }

    @Bean
    public OutboxMetrics outboxMetrics(MeterRegistry meterRegistry) {
        return new OutboxMetrics(meterRegistry);
    }

    /**
     * Con trazabilidad configurada, el contexto de la operacion viaja con el mensaje.
     * Sin ella se usa la implementacion vacia: publicar eventos es el trabajo del
     * outbox, trazarlos es un extra que no puede condicionar su arranque.
     */
    @Bean
    @ConditionalOnBean({Tracer.class, Propagator.class})
    public OutboxTracing micrometerOutboxTracing(Tracer tracer, Propagator propagator) {
        return new MicrometerOutboxTracing(tracer, propagator);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxTracing.class)
    public OutboxTracing noOpOutboxTracing() {
        return new NoOpOutboxTracing();
    }

    /**
     * Un solo hilo a proposito: agrupa los despertares y evita que varias pasadas
     * inmediatas se pisen entre si. El planificador sigue siendo la red de seguridad.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.outbox", name = "immediate-dispatch", havingValue = "true", matchIfMissing = true)
    public ExecutorService outboxDispatchExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "outbox-dispatch");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.outbox", name = "immediate-dispatch", havingValue = "true", matchIfMissing = true)
    public OutboxDispatchTrigger outboxDispatchTrigger(ExecutorService outboxDispatchExecutor,
                                                       OutboxPublisherScheduler outboxPublisherScheduler) {
        return new OutboxDispatchTrigger(outboxDispatchExecutor, outboxPublisherScheduler::publishPendingMessages);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.outbox", name = "immediate-dispatch", havingValue = "true", matchIfMissing = true)
    public OutboxImmediateDispatchListener outboxImmediateDispatchListener(OutboxDispatchTrigger outboxDispatchTrigger) {
        return new OutboxImmediateDispatchListener(outboxDispatchTrigger);
    }
}
