package com.alejandro.mtoconfiguration.core.messaging;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.configuration.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Lo que un mensaje sabe de la operacion que lo genero: quien la pidio y con que identificador.
 * <p>
 * Se lee en el hilo que escribe el outbox, que es el que todavia tiene el contexto: el
 * {@code SecurityContext} de la peticion (o el propagado al hilo de un trabajo por el executor de
 * la aplicacion) y el {@code correlationId} que {@code CorrelationIdFilter} puso en el MDC, o el
 * {@code jobId} que un trabajo pone en su lugar para que todo lo que escribe se agrupe bajo el.
 */
@Component
@RequiredArgsConstructor
public class MessageContextResolver {

    private final CurrentUserService currentUserService;

    /**
     * Nunca nulo: lo que no tiene usuario se dice como {@code SYSTEM} en vez de callarse, para que
     * el consumidor distinga «el emisor no lo dice» de «el emisor dice que fue un proceso».
     */
    public MessageActor currentActor() {
        Optional<Authentication> authentication = currentUserService.getAuthentication();

        if (authentication.isEmpty()) {
            return MessageActor.system();
        }

        String username = currentUserService.getUsername().orElseGet(() -> authentication.get().getName());
        String id = currentUserService.getUserId().orElse(null);

        return MessageActor.of(id, username);
    }

    /** El identificador de la peticion o del trabajo en curso; nulo fuera de ambos. */
    public String currentCorrelationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);

        return value == null || value.isBlank() ? null : value;
    }
}
