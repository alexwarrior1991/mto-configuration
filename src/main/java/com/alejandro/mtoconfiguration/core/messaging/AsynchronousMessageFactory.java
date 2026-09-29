package com.alejandro.mtoconfiguration.core.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Construye el sobre de un mensaje: identificador, origen, fecha, huella y el contexto de la
 * operacion que lo genera (quien y bajo que {@code correlationId}).
 * <p>
 * El contexto se lee aqui, en el momento de crear el mensaje, porque es el unico en el que existe:
 * el evento se escribe en el outbox dentro de la transaccion de negocio, y el relay que lo publica
 * despues corre en un hilo del planificador, sin peticion ni usuario.
 */
@Component
@RequiredArgsConstructor
public class AsynchronousMessageFactory {

    private final AsynchronousMessageHashService hashService;
    private final MessageContextResolver contextResolver;

    @Value("${spring.application.name:mto-configuration}")
    private String applicationName;

    public <T> AsynchronousMessage<T> create(
            String referenceId,
            String eventType,
            T data
    ) {
        return create(UUID.randomUUID(), referenceId, eventType, data);
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
                "PENDING",
                contextResolver.currentActor(),
                contextResolver.currentCorrelationId()
        );

        return message.withMessageHash(hashService.calculate(message));
    }
}
