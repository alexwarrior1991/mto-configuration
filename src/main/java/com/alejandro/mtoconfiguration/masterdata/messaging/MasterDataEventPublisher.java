package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessage;
import com.alejandro.mtoconfiguration.core.messaging.AsynchronousMessageFactory;
import com.alejandro.mtoconfiguration.core.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MasterDataEventPublisher {

    private final AsynchronousMessageFactory asynchronousMessageFactory;
    private final OutboxService outboxService;
    private final MasterDataEntityNameResolver entityNameResolver;
    private final MasterDataEntityIdResolver entityIdResolver;
    private final MasterDataEventPayloadExtractor payloadExtractor;

    public void publish(Object entity, MasterDataOperation operation) {
        String entityName = entityNameResolver.resolve(entity);
        String entityId = entityIdResolver.resolve(entity);
        Map<String, Object> values = payloadExtractor.extract(entity);

        publish(entityName, entityId, operation, values);
    }

    public void publish(
            String entityName,
            String entityId,
            MasterDataOperation operation,
            Map<String, Object> values
    ) {
        MasterDataChangedEvent event = new MasterDataChangedEvent(
                entityName,
                entityId,
                operation,
                values
        );

        String eventType = MasterDataEventTypeBuilder.build(entityName, operation);

        AsynchronousMessage<MasterDataChangedEvent> message = asynchronousMessageFactory.create(
                buildReferenceId(entityName, entityId),
                eventType,
                event
        );

        String routingKey = MasterDataRabbitMqNames.routingKey(entityName, operation);

        outboxService.save(
                entityName,
                entityId,
                eventType,
                MasterDataRabbitMqNames.MASTER_DATA_EXCHANGE,
                routingKey,
                message
        );
    }

    private String buildReferenceId(String entityName, String entityId) {
        return entityName + "-" + entityId;
    }

}
