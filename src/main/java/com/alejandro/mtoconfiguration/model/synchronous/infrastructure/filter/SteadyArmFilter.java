package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

public record SteadyArmFilter(
        Long cantileverId,
        String steadyArmTypeCode,
        Long lengthMin,
        Long lengthMax,
        String searchText
) {
}
