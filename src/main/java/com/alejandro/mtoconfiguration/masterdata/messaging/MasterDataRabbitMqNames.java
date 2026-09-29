package com.alejandro.mtoconfiguration.masterdata.messaging;

/**
 * Nombres del contrato de datos maestros: el exchange y la forma de sus claves de enrutado.
 * <p>
 * Aqui no hay nombres de cola a proposito. Una cola pertenece a quien la consume
 * ({@code mto.stock.master-data.queue}, {@code mto.maintenance.master-data.queue},
 * {@code mto.notification.master-data.queue}), que es quien sabe que limites y que tipo necesita y
 * quien la declara; este servicio solo conoce el exchange al que publica y los patrones con los
 * que esas colas se bindean a el (README_MESSAGING.md, seccion 9.1).
 */
public final class MasterDataRabbitMqNames {

    public static final String MASTER_DATA_EXCHANGE = "mto.master-data.exchange";

    public static final String MASTER_DATA_ROUTING_PREFIX = "mto.master-data";
    public static final String MASTER_DATA_ROUTING_PATTERN = "mto.master-data.#";
    public static final String MASTER_DATA_DELETED_ROUTING_PATTERN = "mto.master-data.*.deleted";

    private MasterDataRabbitMqNames() {
    }

    public static String routingKey(String entityName, MasterDataOperation operation) {
        return MASTER_DATA_ROUTING_PREFIX
                + "."
                + normalize(entityName)
                + "."
                + operation.routingValue();
    }


    private static String normalize(String value) {
        return value.trim()
                .toLowerCase()
                .replace("_", "-")
                .replace(" ", "-");
    }
}
