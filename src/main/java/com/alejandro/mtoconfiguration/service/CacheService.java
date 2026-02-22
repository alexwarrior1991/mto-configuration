package com.alejandro.mtoconfiguration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheService {
    private static final String CACHE = "mto.semi.infraestructure";
    protected static final String CACHE_ITEM = CACHE + ".item";
    protected static final String CACHE_LIST = CACHE + ".list";
    protected static final String CACHE_PAGE = CACHE + ".page";

    @Caching(evict = {
            @CacheEvict(value = CACHE_ITEM, allEntries = true),
            @CacheEvict(value = CACHE_LIST, allEntries = true),
            @CacheEvict(value = CACHE_PAGE, allEntries = true)
    })
    public void cacheClean() {
        log.info("Cleaning cache {}", CACHE);
    }
}
