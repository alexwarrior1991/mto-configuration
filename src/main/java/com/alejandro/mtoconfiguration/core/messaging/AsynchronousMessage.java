package com.alejandro.mtoconfiguration.core.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AsynchronousMessage<T>(
        @NotNull UUID operationId,
        @NotBlank String referenceId,
        @NotBlank String origin,
        @NotNull Instant creationDate,
        @NotBlank String eventType,
        @NotNull T data,
        @NotBlank String messageHash
) {
}
