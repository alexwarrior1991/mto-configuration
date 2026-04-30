package com.alejandro.mtoconfiguration.service.commons;


import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public abstract class AsyncBaseService<T extends BaseDTO, E extends IEntity> extends BaseService<T, E> {

    @SuppressWarnings("unchecked")
    protected BaseService<T, E> getSelfProxy() {
        return (BaseService<T, E>) applicationContext.getBean(this.getClass());
    }

    @Async
    public CompletableFuture<T> getByIdAsync(Long id) {
        return CompletableFuture.completedFuture(getSelfProxy().getById(id));
    }

    @Async
    public CompletableFuture<T> createAsync(T dto) {
        return CompletableFuture.completedFuture(getSelfProxy().create(dto));
    }

    @Async
    public CompletableFuture<List<T>> findAllAsync() {
        return CompletableFuture.completedFuture(getSelfProxy().findAll());
    }

    @Async
    public CompletableFuture<Page<T>> searchAsync(SearchRequestDTO searchRequestDTO) {
        return CompletableFuture.completedFuture(getSelfProxy().search(searchRequestDTO));
    }

    @Async
    public CompletableFuture<List<T>> fetchAndProcessParallel(List<Long> ids, UnaryOperator<T> processor) {

        // En lugar de parallelStream, creamos una lista de CompletableFutures.
        // Cada uno se ejecutará en su propio Virtual Thread gracias al executor de Spring.
        List<CompletableFuture<T>> futures = ids.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        T dto = getSelfProxy().getById(id);
                        return processor.apply(dto);
                    } catch (Exception e) {
                        log.error("Error procesando entidad con ID {}: {}", id, e.getMessage());
                        return null;
                    }
                }, applicationContext.getBean("applicationTaskExecutor", AsyncTaskExecutor.class)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList());

    }

    @Async
    public CompletableFuture<Map<String, List<Object>>> safeBulkUpdateAsync(List<T> dtoList) {
        List<Object> success = new CopyOnWriteArrayList<>();
        List<Object> errors = new CopyOnWriteArrayList<>();

        // Para Virtual Threads, es mejor lanzar muchas tareas independientes
        List<CompletableFuture<Void>> futures = dtoList.stream()
                .map(dto -> CompletableFuture.runAsync(() -> {
                    try {
                        success.add(getSelfProxy().update(dto));
                    } catch (Exception e) {
                        String identifier = (dto != null && dto.getId() != null) ? dto.getId().toString() : "nuevo/nulo";
                        log.error("Fallo en actualización masiva para DTO {}: {}", identifier, e.getMessage());
                        errors.add("ID " + identifier + ": " + e.getMessage());
                    }
                }, applicationContext.getBean("applicationTaskExecutor", AsyncTaskExecutor.class)))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> Map.of("success", success, "errors", errors));
    }

    @Async
    public CompletableFuture<Map<String, Object>> dashboardDataAsync(SearchRequestDTO criteria) {
        CompletableFuture<Page<T>> results = searchAsync(criteria);
        CompletableFuture<List<T>> recents = CompletableFuture.supplyAsync(() -> {
            // Safely retrieves limited recent items for dashboard
            try {
                return getSelfProxy().findAll().stream().limit(10).toList();
            } catch (Exception e) {
                log.warn("No se pudieron recuperar los datos recientes para el dashboard: {}", e.getMessage());
                return new ArrayList<>();
            }
        });

        return CompletableFuture.allOf(results, recents)
                .thenApply(v -> Map.of(
                        "mainPage", results.join(),
                        "recentActivity", recents.join()
                ));
    }

    @Async
    public void streamProcess(List<T> dtoList, Consumer<T> onResult, Consumer<Throwable> onError) {
        dtoList.forEach(dto ->
                // Updates item; handles errors via provided consumer
                CompletableFuture.supplyAsync(() -> getSelfProxy().update(dto))
                        .thenAccept(onResult)
                        .exceptionally(ex -> {
                            log.error("Error en procesamiento de flujo (stream): {}", ex.getMessage());
                            if (onError != null) onError.accept(ex);
                            return null;
                        })
        );
    }
}
