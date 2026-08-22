package com.alejandro.mtoconfiguration.core.exception.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param typeBaseUri     prefijo del campo {@code type} de RFC 9457. Debe ser una URI estable y
 *                        documentada por código de error; no hace falta que resuelva.
 * @param includeStackTrace incluir la traza en la respuesta. Solo para depurar en entornos no
 *                        productivos: la traza revela estructura interna a quien llame.
 */
@ConfigurationProperties(prefix = "configuration.modules.rest.errors")
public record ApiErrorProperties(String typeBaseUri, boolean includeStackTrace) {

    public ApiErrorProperties {
        typeBaseUri = (typeBaseUri == null || typeBaseUri.isBlank())
                ? "https://api.mto-configuration/errors"
                : typeBaseUri.replaceAll("/+$", "");
    }
}
