package com.alejandro.mtoconfiguration.service.commons;


import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
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
    public CompletableFuture<Page<T>> searchAsync(SearchRequestDTO searchRequestDTO) {
        return CompletableFuture.supplyAsync(() -> getSelfProxy().search(searchRequestDTO));
    }

    @Async
    public CompletableFuture<List<T>> fetchAndProcessParallel(List<Long> ids, UnaryOperator<T> processor) {
        return CompletableFuture.supplyAsync(() ->
                ids.parallelStream()
                        .map(id -> {
                            try {
                                T dto = getSelfProxy().getById(id);
                                return processor.apply(dto);
                            } catch (Exception e) {
                                log.error("Error procesando entidad con ID {}: {}", id, e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList()
        );
    }

    @Async
    public CompletableFuture<Map<String, List<Object>>> safeBulkUpdateAsync(List<T> dtoList) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> success = new CopyOnWriteArrayList<>();
            List<Object> errors = new CopyOnWriteArrayList<>();

            dtoList.parallelStream().forEach(dto -> {
                try {
                    success.add(getSelfProxy().update(dto));
                } catch (Exception e) {
                    String identifier = (dto != null && dto.getId() != null) ? dto.getId().toString() : "nuevo/nulo";
                    log.error("Fallo en actualización masiva para DTO {}: {}", identifier, e.getMessage());
                    errors.add("ID " + identifier + ": " + e.getMessage());
                }
            });

            return Map.of("success", success, "errors", errors);
        });
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
