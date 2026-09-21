package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

public record TrackFilter(
        String name,
        String executionPackageName,
        String stationName,
        String searchText,
        /** {@code true} o {@code false} filtran por ese estado; ausente ({@code null}) no filtra. */
        Boolean enabled
) {
    public TrackFilter {
        if (name == null) name = "";
        if (executionPackageName == null) executionPackageName = "";
        if (stationName == null) stationName = "";
        if (searchText == null) searchText = "";
    }
}
