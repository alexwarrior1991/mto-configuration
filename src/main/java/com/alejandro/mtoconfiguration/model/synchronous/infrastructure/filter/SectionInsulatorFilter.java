package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;

public record SectionInsulatorFilter(
        String name,
        String stationName,
        String trackName,
        String switchCode,
        SectionInsulatorInstallationType installationType,
        String searchText,
        boolean enabled
) {
    public SectionInsulatorFilter {
        if (name == null) name = "";
        if (stationName == null) stationName = "";
        if (trackName == null) trackName = "";
        if (switchCode == null) switchCode = "";
        if (searchText == null) searchText = "";
    }
}
