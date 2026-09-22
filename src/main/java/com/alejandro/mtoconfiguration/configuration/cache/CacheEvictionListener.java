package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CacheEvictionListener {

    private final RedisCacheEvictService redisCacheEvictService;


    public CacheEvictionListener(RedisCacheEvictService redisCacheEvictService) {
        this.redisCacheEvictService = redisCacheEvictService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCacheEviction(CacheEvictionEvent event) {
        redisCacheEvictService.evictNormalServiceCaches(event.serviceName());
        // El esquema de via depende de los ocho maestros y el evento solo dice quien escribio.
        redisCacheEvictService.evictTrackSchematics();
    }
}
