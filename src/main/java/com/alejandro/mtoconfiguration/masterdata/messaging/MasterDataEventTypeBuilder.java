package com.alejandro.mtoconfiguration.masterdata.messaging;

public final class MasterDataEventTypeBuilder {

    private MasterDataEventTypeBuilder(){
    }

    public static String build(String entityName, MasterDataOperation operation) {
        return "MASTER_DATA_"
                + normalize(entityName)
                + "_"
                + operation.name();
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase()
                .replace("-", "_")
                .replace(" ", "_");
    }
}
