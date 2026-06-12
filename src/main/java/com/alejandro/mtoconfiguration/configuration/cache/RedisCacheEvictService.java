package com.alejandro.mtoconfiguration.configuration.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
