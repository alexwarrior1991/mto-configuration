package com.alejandro.mtoconfiguration.configuration.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RedisCacheEvictService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheEvictService.class);

    private final RedisConnectionFactory redisConnectionFactory;
    private final String applicationName;

    public RedisCacheEvictService(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${cache.application:mto-configuration}") String applicationName) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.applicationName = applicationName;
    }

    public void evictNormalServiceCaches(String serviceName) {
        evictByPattern(applicationName + "::" + CacheNames.NORMAL_ITEM + "::" + serviceName + ":*");
        evictByPattern(applicationName + "::" + CacheNames.NORMAL_LIST + "::" + serviceName + ":*");
        evictByPattern(applicationName + "::" + CacheNames.NORMAL_PAGE + "::" + serviceName + ":*");
        evictByPattern(applicationName + "::" + CacheNames.NORMAL_SEARCH + "::" + serviceName + ":*");
    }

    public void evictLovCaches() {
        evictByPattern(applicationName + "::" + CacheNames.LOV_ITEM + "::*");
        evictByPattern(applicationName + "::" + CacheNames.LOV_LIST + "::*");
    }

    /**
     * Invalidación granular de un LOV concreto.
     * Los patrones están anclados a los sufijos reales (get&lt;Lov&gt;By* y get&lt;Lov&gt;List)
     * para no arrastrar LOV con prefijo común (Anchorage vs AnchorageFoundation,
     * Portal vs PortalType, Foundation vs FoundationType).
     */
    public void evictLovCaches(String lovName) {
        if (StringUtils.isBlank(lovName)) {
            evictLovCaches();
            return;
        }

        // Claves de MasterDataService: getXByIdAndMapToDTO / getXByCodeAndMapToDTO / getXList
        evictByPattern(lovPattern(CacheNames.LOV_ITEM, masterDataItemKeyPattern(lovName)));
        evictByPattern(lovPattern(CacheNames.LOV_LIST, masterDataListKeyPattern(lovName)));

        // Clave del resolver código -> id (LovReferenceResolver)
        evictByPattern(lovPattern(CacheNames.LOV_ITEM, lovIdByCodeKeyPattern(lovName)));

        // Claves del servicio LOV concreto (AbstractLovCrudService)
        evictByPattern(lovPattern(CacheNames.LOV_ITEM, lovServiceKeyPattern(lovName)));
        evictByPattern(lovPattern(CacheNames.LOV_LIST, lovServiceKeyPattern(lovName)));
    }

    private String masterDataItemKeyPattern(String lovName) {
        return "MasterDataService:get" + lovName + "By*";
    }

    private String masterDataListKeyPattern(String lovName) {
        return "MasterDataService:get" + lovName + "List";
    }

    private String lovIdByCodeKeyPattern(String lovName) {
        return "lovIdByCode:" + lovName + ":*";
    }

    private String lovServiceKeyPattern(String lovName) {
        return lovName + "Service:find*";
    }

    private String lovPattern(String cacheName, String keyPattern) {
        return applicationName + "::" + cacheName + "::" + keyPattern;
    }

    private void evictByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(1000)
                .build();

        List<byte[]> keysToDelete = new ArrayList<>();

        try (RedisConnection connection = redisConnectionFactory.getConnection();
             Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {

            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }

            if (!keysToDelete.isEmpty()) {
                connection.keyCommands().del(keysToDelete.toArray(new byte[0][]));
            }

            log.info("Redis cache eviction completed. Pattern: {}, deleted keys: {}", pattern, keysToDelete.size());

        } catch (Exception e) {
            log.error("Error evicting Redis cache by pattern: {}", pattern, e);
        }
    }
}
