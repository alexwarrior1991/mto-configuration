package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

import java.math.BigDecimal;

public record CantileverFilter(
        Long profileId,
        String profileCode,
        String cantileverTypeCode,
        BigDecimal cwHeightMin,
        BigDecimal cwHeightMax,
        BigDecimal staggerMin,
        BigDecimal staggerMax,
        String searchText
) {

}
