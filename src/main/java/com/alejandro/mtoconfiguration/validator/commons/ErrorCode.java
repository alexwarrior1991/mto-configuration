package com.alejandro.mtoconfiguration.validator.commons;

import java.util.Map;
import java.util.Objects;

public record ErrorCode(
        String code,
        ErrorType type,
        Severity severity,
        String messageTemplate,
        boolean retryable,
        Map<String, String> metadata
) {
    public ErrorCode {
        Objects.requireNonNull(code, "code can not be null");
        Objects.requireNonNull(type, "type can not be null");
        Objects.requireNonNull(severity, "severity can not be null");
        Objects.requireNonNull(messageTemplate, "messageTemplate can not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ErrorCode(String code, ErrorType type, Severity severity, String messageTemplate, boolean retryable) {
        this(code, type, severity, messageTemplate, retryable, Map.of());
    }

    /**
     * Devuelve el mensaje formateado usando String::formatted.
     * Ej: template "Campo %s" → format("nombre") = "Campo nombre"
     */
    public String format(Object... args) {
        return messageTemplate.formatted(args);
    }
}
