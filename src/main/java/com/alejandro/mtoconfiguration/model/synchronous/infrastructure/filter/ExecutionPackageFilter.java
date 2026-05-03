package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.filter;

import java.time.LocalDate;

public record ExecutionPackageFilter(
        String name,
        String companyName,
        LocalDate startDate,
        LocalDate endDate,
        String searchText,
        boolean enabled
) {
    public ExecutionPackageFilter {
        if (name == null) name = "";
        if (searchText == null) searchText = "";
    }
}
