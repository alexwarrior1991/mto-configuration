package com.alejandro.mtoconfiguration.masterdata.messaging;

public enum MasterDataOperation {

    CREATED("created"),
    UPDATED("updated"),
    DELETED("deleted");

    private final String routingValue;

    MasterDataOperation(String routingValue) {
        this.routingValue = routingValue;
    }

    public String routingValue() {
        return routingValue;
    }
}
