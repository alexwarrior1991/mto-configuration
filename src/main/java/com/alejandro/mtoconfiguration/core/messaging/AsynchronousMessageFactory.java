package com.alejandro.mtoconfiguration.core.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AsynchronousMessageFactory {

    private final AsynchronousMessageHashService hashService;

    @Value("${spring.application.name:mto-configuration}")
    private String applicationName;

    public <T> AsynchronousMessage<T> create(
            String referenceId,
            String eventType,
            T data
    ) {
        AsynchronousMessage<T> message = new AsynchronousMessage<>(
                UUID.randomUUID(),
                referenceId,
                applicationName,
                Instant.now(),
                eventType,
                data,
                "PENDING"
        );

        return withHash(message);
    }

    public <T> AsynchronousMessage<T> create(
            UUID operationId,
            String referenceId,
            String eventType,
            T data
    ) {
        AsynchronousMessage<T> message = new AsynchronousMessage<>(
                operationId,
                referenceId,
                applicationName,
                Instant.now(),
                eventType,
                data,
                "PENDING"
        );

        return withHash(message);
    }

    private <T> AsynchronousMessage<T> withHash(AsynchronousMessage<T> message) {
        String hashCode = hashService.calculate(message);

        return new AsynchronousMessage<>(
                message.operationId(),
                message.referenceId(),
                message.origin(),
                message.creationDate(),
                message.eventType(),
                message.data(),
                hashCode
        );
    }
}
