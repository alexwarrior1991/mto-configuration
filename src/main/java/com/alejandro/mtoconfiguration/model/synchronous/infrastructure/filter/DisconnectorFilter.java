package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

public record DisconnectorFilter(
        String name,
        String stationName,
        String functionName,
        String searchText,
        boolean onLoad
) {
    public DisconnectorFilter {
        if (name == null) name = "";
        if (stationName == null) stationName = "";
        if (functionName == null) functionName = "";
        if (searchText == null) searchText = "";
    }
}
