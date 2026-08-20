package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.model.commons.CachedPageDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Bean delegado para cachear páginas. Existe para que la llamada desde
 * BaseService cruce el proxy de Spring y @Cacheable se aplique de verdad,
 * en lugar de recurrir a auto-invocación.
 * <p>
 * La clave se pasa ya construida (key = "#cacheKey"): con el keyGenerator por
 * defecto se hashearía el lambda del Supplier y la clave cambiaría en cada llamada.
 * Hacen falta dos métodos porque cacheNames no admite SpEL y NORMAL_PAGE y
 * NORMAL_SEARCH tienen TTL distinto.
 */
@Service
public class PageCacheService {

    @Cacheable(
            cacheNames = CacheNames.NORMAL_PAGE,
            key = "#cacheKey",
            unless = "#result == null || #result.isEmpty()"
    )
    public <T> CachedPageDTO<T> getPage(String cacheKey, Supplier<Page<T>> loader) {
        return CachedPageDTO.from(loader.get());
    }

    @Cacheable(
            cacheNames = CacheNames.NORMAL_SEARCH,
            key = "#cacheKey",
            unless = "#result == null || #result.isEmpty()"
    )
    public <T> CachedPageDTO<T> getSearch(String cacheKey, Supplier<Page<T>> loader) {
        return CachedPageDTO.from(loader.get());
    }
}
