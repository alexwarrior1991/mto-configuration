package com.alejandro.mtoconfiguration.core.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties outboxProperties;

    @Scheduled(fixedDelayString = "${app.outbox.publisher-fixed-delay:5000}")
    @Transactional
    public void publishPendingMessages(){
        Instant now = Instant.now();

        outboxMessageRepository
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OutboxStatus.PENDING, now)
                .forEach(this::publish);
    }

    private void publish(OutboxMessage outboxMessage) {
        try {
            Message message = MessageBuilder
                    .withBody(outboxMessage.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(outboxMessage.getId().toString())
                    .setHeader("eventType", outboxMessage.getEventType())
                    .setHeader("aggregateType", outboxMessage.getAggregateType())
                    .setHeader("aggregateId", outboxMessage.getAggregateId())
                    .build();

            rabbitTemplate.send(
                    outboxMessage.getExchangeName(),
                    outboxMessage.getRoutingKey(),
                    message
                );

            outboxMessage.setStatus(OutboxStatus.PUBLISHED);
            outboxMessage.setPublishedAt(Instant.now());
            outboxMessage.setLastError(null);

            log.info(
                    "Outbox message published. id={}, eventType={}, exchange={}, routingKey={}",
                    outboxMessage.getId(),
                    outboxMessage.getEventType(),
                    outboxMessage.getExchangeName(),
                    outboxMessage.getRoutingKey()
            );

        } catch (Exception exception) {
            handlePublishError(outboxMessage, exception);
        }
    }

    private void handlePublishError(OutboxMessage outboxMessage, Exception exception) {
        int attempts = outboxMessage.getAttempts() + 1;
        outboxMessage.setAttempts(attempts);
        outboxMessage.setLastError(exception.getMessage());

        if (attempts >= outboxMessage.getMaxAttempts()) {
            outboxMessage.setStatus(OutboxStatus.FAILED);
        } else {
            outboxMessage.setNextAttemptAt(
                    Instant.now().plus(outboxProperties.getInitialRetryDelay().multipliedBy(attempts))
            );
        }

        log.error(
                "Error publishing outbox message. id={}, attempts={}, eventType={}",
                outboxMessage.getId(),
                attempts,
                outboxMessage.getEventType(),
                exception
        );
    }

}
