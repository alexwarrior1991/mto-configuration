package com.alejandro.mtoconfiguration.core.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * El sobre de todo lo que este servicio publica.
 * <p>
 * Las siete primeras claves son el contrato original, y {@code messageHash} se calcula solo sobre
 * ellas. {@code actor} y {@code correlationId} se anadieron despues, para que un consumidor sepa
 * quien hizo el cambio y bajo que peticion o trabajo: un consumidor anterior las ignora, y uno
 * nuevo las tolera ausentes, porque en el contrato solo se anaden claves.
 *
 * @param actor         quien pidio la operacion, clasificado por este servicio; nunca nulo en lo
 *                      que se publica hoy, pero opcional en el contrato
 * @param correlationId el {@code X-Correlation-Id} de la peticion, o el {@code jobId} del trabajo
 *                      en segundo plano que escribio el evento; nulo fuera de ambos
 */
public record AsynchronousMessage<T>(
        @NotNull UUID operationId,
        @NotBlank String referenceId,
        @NotBlank String origin,
        @NotNull Instant creationDate,
        @NotBlank String eventType,
        @NotNull T data,
        @NotBlank String messageHash,
        MessageActor actor,
        String correlationId
) {

    /** El mismo mensaje con su huella ya calculada. */
    public AsynchronousMessage<T> withMessageHash(String hash) {
        return new AsynchronousMessage<>(
                operationId,
                referenceId,
                origin,
                creationDate,
                eventType,
                data,
                hash,
                actor,
                correlationId
        );
    }
}
