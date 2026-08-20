package com.alejandro.mtoconfiguration.configuration.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LovCacheEvictionListener {
    private final RedisCacheEvictService redisCacheEvictService;

    public LovCacheEvictionListener(RedisCacheEvictService redisCacheEvictService) {
        this.redisCacheEvictService = redisCacheEvictService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLovCacheEviction(LovCacheEvictionEvent event) {
        redisCacheEvictService.evictLovCaches(event.lovName());
    }
}
