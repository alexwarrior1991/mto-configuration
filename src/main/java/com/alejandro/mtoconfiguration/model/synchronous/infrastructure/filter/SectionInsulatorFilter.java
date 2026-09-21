package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;

public record SectionInsulatorFilter(
        String name,
        String stationName,
        String trackName,
        String switchCode,
        SectionInsulatorInstallationType installationType,
        String searchText,
        /** {@code true} o {@code false} filtran por ese estado; ausente ({@code null}) no filtra. */
        Boolean enabled
) {
    public SectionInsulatorFilter {
        if (name == null) name = "";
        if (stationName == null) stationName = "";
        if (trackName == null) trackName = "";
        if (switchCode == null) switchCode = "";
        if (searchText == null) searchText = "";
    }
}
