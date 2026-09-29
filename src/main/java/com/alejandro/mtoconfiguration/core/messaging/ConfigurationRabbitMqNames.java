package com.alejandro.mtoconfiguration.core.messaging;

import java.util.Locale;

/**
 * Nombres del exchange propio de este servicio y de lo que se enruta por el.
 * <p>
 * Es un exchange distinto de {@code mto.master-data.exchange} a proposito: aquel es el contrato de
 * los datos maestros, con consumidores que lo escuchan entero ({@code mto.master-data.#}) y que no
 * tienen por que recibir el final de un trabajo. Lo que este servicio cuenta de si mismo sale por
 * aqui, y cada consumidor bindea su cola a lo que le interese.
 */
public final class ConfigurationRabbitMqNames {

    public static final String CONFIGURATION_EXCHANGE = "mto.configuration.exchange";

    public static final String CONFIGURATION_ROUTING_PREFIX = "mto.configuration";
    public static final String CONFIGURATION_ROUTING_PATTERN = "mto.configuration.#";

    private static final String EVENT_TYPE_PREFIX = "CONFIGURATION";

    private ConfigurationRabbitMqNames() {
    }

    /** {@code mto.configuration.<entidad>.<evento>}: lo que decide a que colas llega el mensaje. */
    public static String routingKey(String entityName, String eventName) {
        return CONFIGURATION_ROUTING_PREFIX + "." + normalize(entityName) + "." + normalize(eventName);
    }

    /** {@code CONFIGURATION_<ENTIDAD>_<EVENTO>}: el {@code eventType} del sobre y de la cabecera. */
    public static String eventType(String entityName, String eventName) {
        return EVENT_TYPE_PREFIX + "_" + constant(entityName) + "_" + constant(eventName);
    }

    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("_", "-")
                .replace(" ", "-");
    }

    private static String constant(String value) {
        return normalize(value)
                .toUpperCase(Locale.ROOT)
                .replace("-", "_");
    }
}
