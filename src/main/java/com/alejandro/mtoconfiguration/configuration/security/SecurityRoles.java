package com.alejandro.mtoconfiguration.configuration.security;

/**
 * Roles de cliente de Keycloak que la API comprueba, sin el prefijo {@code ROLE_} porque es el
 * formato que esperan {@code hasRole(...)} y {@code hasAnyRole(...)}.
 * <p>
 * El modelo tiene dos niveles: estos son permisos concretos y se declaran como roles de cliente de
 * {@code mto-configuration-api}; los perfiles de negocio ({@code mto-viewer}, {@code mto-editor},
 * {@code mto-admin}, {@code mto-auditor}, {@code mto-ops}) son roles <em>compuestos</em> de realm
 * que agrupan a estos. Así se cambia lo que puede hacer un perfil desde Keycloak sin desplegar.
 * <p>
 * Los nombres en Keycloak van en minúsculas y con guiones ({@code config-read}): el
 * {@link KeycloakJwtAuthenticationConverter} los normaliza a mayúsculas con guion bajo, que es la
 * forma que aparece aquí. Evítense los dos puntos como separador, porque sobreviven a la
 * normalización y obligarían a escribir {@code hasRole("CONFIG:READ")}.
 */
public final class SecurityRoles {

    private SecurityRoles() {
    }

    /** Lectura de infraestructura y listas de valores. */
    public static final String CONFIG_READ = "CONFIG_READ";

    /** Alta y modificación de infraestructura, registro a registro. */
    public static final String CONFIG_WRITE = "CONFIG_WRITE";

    /** Borrado y cancelación. Separado de la escritura a propósito. */
    public static final String CONFIG_DELETE = "CONFIG_DELETE";

    /** Cargas masivas: un error aquí afecta a miles de registros de una vez. */
    public static final String CONFIG_IMPORT = "CONFIG_IMPORT";

    /** Mantenimiento de listas de valores: cambian el vocabulario de todo el dominio. */
    public static final String LOV_MANAGE = "LOV_MANAGE";

    /** Consulta del histórico de revisiones de Envers. */
    public static final String CONFIG_AUDIT = "CONFIG_AUDIT";

    /** Lectura de los endpoints de operación expuestos por Actuator. */
    public static final String OPS_METRICS = "OPS_METRICS";

    /**
     * Operaciones de Actuator que <b>modifican</b> algo, como el redrive de los mensajes en estado
     * FAILED del outbox.
     *
     * <p>Va aparte de {@link #OPS_METRICS} porque son cosas distintas: leer metricas es observar, y
     * republicar mensajes cambia el estado del sistema. Con un solo rol, cualquiera que pudiera
     * consultar Prometheus podria tambien disparar un redrive.</p>
     *
     * <p>Se concede por clase de operacion y no por endpoint concreto: asi, si manana se expone
     * otro endpoint de escritura —cambiar un nivel de log, por ejemplo— no hereda en silencio el
     * permiso de lectura.</p>
     */
    public static final String OPS_WRITE = "OPS_WRITE";
}
