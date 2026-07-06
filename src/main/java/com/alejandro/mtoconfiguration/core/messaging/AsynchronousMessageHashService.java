package com.alejandro.mtoconfiguration.core.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AsynchronousMessageHashService {

    private final ObjectMapper objectMapper;

    public <T> String calculate(AsynchronousMessage<T> message){
        return calculate(
                message.operationId(),
                message.referenceId(),
                message.origin(),
                message.creationDate(),
                message.eventType(),
                message.data()
        );
    }

    public <T> boolean isValid(AsynchronousMessage<T> message) {
        byte[] expected = calculate(message).getBytes(StandardCharsets.UTF_8);
        byte[] actual = message.messageHash().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private <T> String calculate(
            UUID operationId,
            String referenceId,
            String origin,
            Instant creationDate,
            String eventType,
            T data
    ){
        try {
            String rawValue = objectMapper.writeValueAsString(new HashSource<>(
                    operationId,
                    referenceId,
                    origin,
                    creationDate,
                    eventType,
                    data
            ));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Error calculating asynchronous message hash", exception);
        }
    }

    private record HashSource<T>(
            UUID operationId,
            String referenceId,
            String origin,
            Instant creationDate,
            String eventType,
            T data
    ) {
    }
}
