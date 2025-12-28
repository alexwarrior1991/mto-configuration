package com.alejandro.mtoconfiguration.validator.commons;

import java.util.Map;

public enum StandardErrorCodes implements ErrorCatalogEntry{
    VALIDATION_REQUIRED_FIELD(
            new ErrorCode(
                    ErrorCodes.VALIDATION_REQUIRED_FIELD,
                    ErrorType.VALIDATION,
                    Severity.ERROR,
                    "El campo '%s' es obligatorio",
                    false
            )
    ),

    VALIDATION_INVALID_FORMAT(
            new ErrorCode(
                    ErrorCodes.VALIDATION_INVALID_FORMAT,
                    ErrorType.VALIDATION,
                    Severity.ERROR,
                    "El valor '%s' no tiene el formato correcto para el campo '%s'",
                    false
            )
    ),

    VALIDATION_OUT_OF_RANGE(
            new ErrorCode(
                    ErrorCodes.VALIDATION_OUT_OF_RANGE,
                    ErrorType.VALIDATION,
                    Severity.ERROR,
                    "El valor '%s' está fuera del rango permitido para el campo '%s'",
                    false
            )
    ),

    // --- NEGOCIO ---
    BUSINESS_RULE_VIOLATION(
            new ErrorCode(
                    ErrorCodes.BUSINESS_RULE_VIOLATION,
                    ErrorType.BUSINESS,
                    Severity.ERROR,
                    "Regla de negocio violada: %s",
                    false
            )
    ),

    DUPLICATED_RESOURCE(
            new ErrorCode(
                    ErrorCodes.DUPLICATED_RESOURCE,
                    ErrorType.BUSINESS,
                    Severity.WARNING,
                    "El recurso con identificador '%s' ya existe",
                    false
            )
    ),

    // --- NOT FOUND ---
    RESOURCE_NOT_FOUND(
            new ErrorCode(
                    ErrorCodes.RESOURCE_NOT_FOUND,
                    ErrorType.NOT_FOUND,
                    Severity.WARNING,
                    "No se ha encontrado el recurso con identificador '%s'",
                    false
            )
    ),

    ENDPOINT_NOT_FOUND(
            new ErrorCode(
                    ErrorCodes.ENDPOINT_NOT_FOUND,
                    ErrorType.NOT_FOUND,
                    Severity.WARNING,
                    "La ruta solicitada no existe",
                    false
            )
    ),

    // --- SEGURIDAD ---
    UNAUTHORIZED(
            new ErrorCode(
                    ErrorCodes.UNAUTHORIZED,
                    ErrorType.SECURITY,
                    Severity.ERROR,
                    "Autenticación requerida o inválida",
                    false
            )
    ),

    FORBIDDEN(
            new ErrorCode(
                    ErrorCodes.FORBIDDEN,
                    ErrorType.SECURITY,
                    Severity.ERROR,
                    "No tiene permisos para realizar esta acción",
                    false
            )
    ),

    INVALID_CREDENTIALS(
            new ErrorCode(
                    ErrorCodes.INVALID_CREDENTIALS,
                    ErrorType.SECURITY,
                    Severity.WARNING,
                    "Credenciales inválidas",
                    false
            )
    ),

    // --- TÉCNICOS / INFRA ---
    DATABASE_ERROR(
            new ErrorCode(
                    ErrorCodes.DATABASE_ERROR,
                    ErrorType.TECHNICAL,
                    Severity.CRITICAL,
                    "Error de acceso a base de datos",
                    true
            )
    ),

    INTEGRATION_TIMEOUT(
            new ErrorCode(
                    ErrorCodes.INTEGRATION_TIMEOUT,
                    ErrorType.INTEGRATION,
                    Severity.ERROR,
                    "Timeout llamando al sistema externo '%s'",
                    true
            )
    ),

    INTEGRATION_ERROR(
            new ErrorCode(
                    ErrorCodes.INTEGRATION_ERROR,
                    ErrorType.INTEGRATION,
                    Severity.ERROR,
                    "Error llamando al sistema externo '%s'",
                    true
            )
    ),

    CONCURRENCY_CONFLICT(
            new ErrorCode(
                    ErrorCodes.CONCURRENCY_CONFLICT,
                    ErrorType.CONCURRENCY,
                    Severity.WARNING,
                    "Conflicto de concurrencia detectado. Inténtelo de nuevo.",
                    true
            )
    ),

    UNEXPECTED_ERROR(
            new ErrorCode(
                    ErrorCodes.UNEXPECTED_ERROR,
                    ErrorType.TECHNICAL,
                    Severity.CRITICAL,
                    "Se ha producido un error inesperado",
                    true,
                    Map.of("category", "UNEXPECTED")
            )
    );

    private final ErrorCode errorCode;

    StandardErrorCodes(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public ErrorCode errorCode() {
        return errorCode;
    }
}
