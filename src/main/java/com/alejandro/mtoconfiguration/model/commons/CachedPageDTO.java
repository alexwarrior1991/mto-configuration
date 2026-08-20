package com.alejandro.mtoconfiguration.model.commons;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

public record CachedPageDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        List<CachedSortOrderDTO> sort
) {

    public static <T> CachedPageDTO<T> from(Page<T> page) {
        List<CachedSortOrderDTO> sort = page.getSort()
                .stream()
                .map(CachedSortOrderDTO::from)
                .toList();

        return new CachedPageDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                sort
        );
    }

    public Page<T> toPage() {
        return new PageImpl<>(content, PageRequest.of(page, size, toSort()), totalElements);
    }

    private Sort toSort() {
        if (sort == null || sort.isEmpty()) {
            return Sort.unsorted();
        }

        return Sort.by(sort.stream()
                .map(CachedSortOrderDTO::toOrder)
                .toList());
    }

    public boolean isEmpty() {
        return content == null || content.isEmpty();
    }

    public record CachedSortOrderDTO(
            String property,
            Sort.Direction direction,
            Sort.NullHandling nullHandling,
            boolean ignoreCase
    ) {
        private static CachedSortOrderDTO from(Sort.Order order) {
            return new CachedSortOrderDTO(
                    order.getProperty(),
                    order.getDirection(),
                    order.getNullHandling(),
                    order.isIgnoreCase()
            );
        }

        private Sort.Order toOrder() {
            Sort.Order order = new Sort.Order(direction, property, nullHandling);
            return ignoreCase ? order.ignoreCase() : order;
        }
    }
}