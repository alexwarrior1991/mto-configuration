package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;

/**
 * Filtros de {@code POST /track-access-points/filter}, combinados con AND. Los textos en blanco no
 * filtran; los demás, solo si vienen ({@code null} no filtra, {@code false} sí).
 */
public record TrackAccessPointFilter(
        String name,
        /** Los de esa vía, por su id. */
        Long trackId,
        String trackName,
        String stationName,
        TrackAccessPointKind kind,
        TrackAccessPointSide side,
        Boolean roadRailAccess,
        /** El nombre, la vía o la estación contienen el texto. */
        String searchText,
        Boolean enabled
) {
    public TrackAccessPointFilter {
        if (name == null) name = "";
        if (trackName == null) trackName = "";
        if (stationName == null) stationName = "";
        if (searchText == null) searchText = "";
    }
}
