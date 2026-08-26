package com.alejandro.mtoconfiguration.core.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.messaging.signature")
public class MessageSignatureProperties {

    /**
     * Secreto compartido con los consumidores.
     * <p>
     * Con secreto, la firma es un HMAC-SHA256 y protege de manipulacion: quien altere
     * el mensaje no puede recalcularla sin conocerlo. Sin secreto se degrada a un
     * SHA-256 simple, que detecta corrupcion pero NO manipulacion, porque cualquiera
     * que cambie el contenido puede recalcular el hash.
     * <p>
     * Se deja vacio por defecto: repartir un secreto entre servicios es una decision
     * de explotacion, y hacerlo obligatorio impediria arrancar a quien no lo necesite.
     */
    private String secret;

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }
}
