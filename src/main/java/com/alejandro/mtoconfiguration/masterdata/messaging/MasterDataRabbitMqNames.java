package com.alejandro.mtoconfiguration.masterdata.messaging;

public final class MasterDataRabbitMqNames {

    public static final String MASTER_DATA_EXCHANGE = "mto.master-data.exchange";

    public static final String MASTER_DATA_EVENTS_QUEUE = "mto.master-data.events.queue";
    public static final String MASTER_DATA_CACHE_QUEUE = "mto.master-data.cache.queue";
    public static final String MASTER_DATA_AUDIT_QUEUE = "mto.master-data.audit.queue";
    public static final String MASTER_DATA_DELETED_QUEUE = "mto.master-data.deleted.queue";

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
