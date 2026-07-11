package com.alejandro.mtoconfiguration.core.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;


    public void save(
            String aggregateType,
            String aggregateId,
            String eventType,
            String exchangeName,
            String routingKey,
            Object payload
    ) {

        try {

            Instant now = Instant.now();

            OutboxMessage message = new OutboxMessage();
            message.setId(UUID.randomUUID());
            message.setAggregateType(aggregateType);
            message.setAggregateId(aggregateId);
            message.setEventType(eventType);
            message.setExchangeName(exchangeName);
            message.setRoutingKey(routingKey);
            message.setPayload(objectMapper.writeValueAsString(payload));
            message.setStatus(OutboxStatus.PENDING);
            message.setAttempts(0);
            message.setMaxAttempts(outboxProperties.getMaxAttempts());
            message.setCreatedAt(now);
            message.setNextAttemptAt(now);

            outboxMessageRepository.save(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Error creating outbox message", exception);
        }

    }

}
