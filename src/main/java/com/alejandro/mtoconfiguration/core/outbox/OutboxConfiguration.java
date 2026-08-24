package com.alejandro.mtoconfiguration.core.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
