package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

public record SectionInsulatorFilter(
        String name,
        String stationName,
        String searchText,
        boolean enabled
) {
    public SectionInsulatorFilter {
        if (name == null) name = "";
        if (stationName == null) stationName = "";
        if (searchText == null) searchText = "";
    }
}
