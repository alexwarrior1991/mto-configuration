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
        // Las claves del LOV y el esquema de via, que lleva codigos de catalogo (tipo de poste, de
        // mensula, seccionamiento...), en una sola conexion.
        redisCacheEvictService.evictAfterLovWrite(event.lovName());
    }
}
