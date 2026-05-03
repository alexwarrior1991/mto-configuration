package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

public record TrackFilter(
        String name,
        String executionPackageName,
        String stationName,
        String searchText,
        boolean enabled
) {
    public TrackFilter {
        if (name == null) name = "";
        if (executionPackageName == null) executionPackageName = "";
        if (stationName == null) stationName = "";
        if (searchText == null) searchText = "";
    }
}
