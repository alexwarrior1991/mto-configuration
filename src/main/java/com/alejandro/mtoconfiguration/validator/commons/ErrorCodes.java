package com.alejandro.mtoconfiguration.validator.commons;

public final class ErrorCodes {

    private ErrorCodes() {
        // Evitar instanciación
    }

    // --- VALIDACIÓN ---
    public static final String VALIDATION_REQUIRED_FIELD = "VAL-001";
    public static final String VALIDATION_INVALID_FORMAT = "VAL-002";
    public static final String VALIDATION_OUT_OF_RANGE   = "VAL-003";

    // --- NEGOCIO ---
    public static final String BUSINESS_RULE_VIOLATION = "BUS-001";
    public static final String DUPLICATED_RESOURCE     = "BUS-002";

    // --- NOT FOUND ---
    public static final String RESOURCE_NOT_FOUND  = "NOT-001";
    public static final String ENDPOINT_NOT_FOUND  = "NOT-002";

    // --- SEGURIDAD ---
    public static final String UNAUTHORIZED         = "SEC-001";
    public static final String FORBIDDEN            = "SEC-002";
    public static final String INVALID_CREDENTIALS  = "SEC-003";

    // --- TÉCNICOS / INFRA ---
    public static final String DATABASE_ERROR       = "TEC-001";
    public static final String UNEXPECTED_ERROR     = "TEC-999";

    // --- INTEGRACIÓN ---
    public static final String INTEGRATION_TIMEOUT  = "INT-001";
    public static final String INTEGRATION_ERROR    = "INT-002";

    // --- CONCURRENCIA ---
    public static final String CONCURRENCY_CONFLICT = "CON-001";
}
