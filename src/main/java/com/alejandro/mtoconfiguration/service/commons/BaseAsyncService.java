package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

@Slf4j
public abstract class BaseAsyncService<T extends BaseDTO, E extends CRUDEntity, S extends CRUDService<T, E>> {

    protected abstract S getService();

    @Async("applicationTaskExecutor")
    public CompletableFuture<T> getByIdAsync(Long id) {
        return CompletableFuture.completedFuture(getService().getById(id));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<T> createAsync(T dto) {
        return CompletableFuture.completedFuture(getService().create(dto));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<T>> bulkCreateAsync(List<T> dtoList) {
        return CompletableFuture.completedFuture(getService().bulkCreate(dtoList));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<T> updateAsync(T dto) {
        return CompletableFuture.completedFuture(getService().update(dto));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<T>> bulkUpdateAsync(List<T> dtoList) {
        return CompletableFuture.completedFuture(getService().bulkUpdate(dtoList));
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<List<T>> findAllAsync() {
        return CompletableFuture.completedFuture(getService().findAll());
    }

    @Async("applicationTaskExecutor")
    public CompletableFuture<Page<T>> searchAsync(SearchRequestDTO searchRequestDTO) {
        return CompletableFuture.completedFuture(getService().search(searchRequestDTO));
    }



    @Async("applicationTaskExecutor")
    public CompletableFuture<List<T>> fetchAndProcessAsync(List<Long> ids, UnaryOperator<T> processor) {
        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<T> results = ids.stream()
                .map(id -> processSafely(id, processor))
                .filter(Objects::nonNull)
                .toList();

        return CompletableFuture.completedFuture(results);
    }

    private T processSafely(Long id, UnaryOperator<T> processor) {
        try {
            T dto = getService().getById(id);
            return processor.apply(dto);
        } catch (Exception e) {
            log.error("Error procesando entidad con ID {}: {}", id, e.getMessage(), e);
            return null;
        }
    }
}
