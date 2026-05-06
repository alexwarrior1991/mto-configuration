package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

import java.math.BigDecimal;

public record ProfileFilter(
        String profileId,
        BigDecimal kp,
        Long trackId,
        String trackName,
        String stationName,
        String anchorageCode,
        String anchorageFoundationCode,
        String foundationCode,
        String poleTypeCode,
        String portalCode,
        String profileStatusCode,
        String returnSupportCode,
        String sectioningCode,
        String searchText
) {
    public ProfileFilter {
        profileId = normalize(profileId);
        trackName = normalize(trackName);
        stationName = normalize(stationName);
        anchorageCode = normalize(anchorageCode);
        anchorageFoundationCode = normalize(anchorageFoundationCode);
        foundationCode = normalize(foundationCode);
        poleTypeCode = normalize(poleTypeCode);
        portalCode = normalize(portalCode);
        profileStatusCode = normalize(profileStatusCode);
        returnSupportCode = normalize(returnSupportCode);
        sectioningCode = normalize(sectioningCode);
        searchText = normalize(searchText);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
