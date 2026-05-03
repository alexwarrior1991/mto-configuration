package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

public record StationFilter(
        String name,
        String executionPackageName,
        String trackName,
        String searchText
) {
    public StationFilter {
        if (name == null) name = "";
        if (executionPackageName == null) executionPackageName = "";
        if (trackName == null) trackName = "";
        if (searchText == null) searchText = "";
    }
}
