package com.alejandro.mtoconfiguration.core.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private boolean enabled = true;

    private int maxAttempts = 10;

    private Duration initialRetryDelay = Duration.ofSeconds(5);

    private Duration publisherFixedDelay = Duration.ofSeconds(5);
}
