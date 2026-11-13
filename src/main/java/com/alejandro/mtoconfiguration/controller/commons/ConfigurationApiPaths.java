package com.alejandro.mtoconfiguration.controller.commons;

public final class ConfigurationApiPaths {

    private ConfigurationApiPaths() {
        // Utility class
    }

    public static final String BASE_PATH = "/api/v1/configuration";
    public static final String ASYNC_BASE_PATH = BASE_PATH + "/async";

    /** El historial de cambios de una fila, relativo a la ruta de su recurso. */
    public static final String REVISIONS = "/{id}/revisions";

    /**
     * Las rutas de historial, para la regla de seguridad: {@code /{recurso}/{id}/revisions}, con el
     * recurso en un solo segmento ({@code PathPattern} solo admite {@code /**} al final).
     */
    public static final String REVISIONS_PATTERN = BASE_PATH + "/*/*/revisions";
}
