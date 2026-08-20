package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import com.alejandro.mtoconfiguration.model.commons.CachedPageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {
        RedisCacheConfig.class,
        RedisCacheKeyGenerator.class,
        RedisCacheIT.RedisCacheTestConfiguration.class
})
class RedisCacheIT {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RedisBackedTestService service;

    @Autowired
    private RedisBackedTestRepository repository;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("cache.redis.allowed-subtypes[0]", () -> "java.util");
        registry.add("cache.redis.allowed-subtypes[1]", () -> "org.springframework.data.domain");
        registry.add("cache.redis.allowed-subtypes[2]", () -> "com.alejandro.mtoconfiguration");
    }

    @BeforeEach
    void setUp() {
        reset(repository);
    }

    @Test
    void shouldReadEveryCacheFromRedisWithoutTouchingRepositoryTwice() {
        assertCacheHit(CacheNames.NORMAL_ITEM, () -> service.normalItem("normal-item"));
        assertCacheHit(CacheNames.NORMAL_LIST, () -> service.normalList("normal-list"));
        assertCacheHit(CacheNames.NORMAL_PAGE, () -> service.normalPage("normal-page"));
        assertCacheHit(CacheNames.NORMAL_SEARCH, () -> service.normalSearch("normal-search"));
        assertCacheHit(CacheNames.LOV_ITEM, () -> service.lovItem("lov-item"));
        assertCacheHit(CacheNames.LOV_LIST, () -> service.lovList("lov-list"));
    }

    private <T> void assertCacheHit(String cacheName, Supplier<T> cachedCall) {
        TestValue expected = new TestValue(cacheName, "value");
        when(repository.load(cacheName)).thenReturn(expected);

        T first = cachedCall.get();
        T second = cachedCall.get();

        assertThat(second).isEqualTo(first);
        verify(repository, times(1)).load(cacheName);
    }

    @Configuration
    static class RedisCacheTestConfiguration {

        @Bean
        RedisBackedTestRepository redisBackedTestRepository() {
            return Mockito.mock(RedisBackedTestRepository.class);
        }

        @Bean
        RedisBackedTestService redisBackedTestService(RedisBackedTestRepository repository) {
            return new RedisBackedTestService(repository);
        }
    }

    interface RedisBackedTestRepository {
        TestValue load(String cacheName);
    }

    static class RedisBackedTestService {
        private final RedisBackedTestRepository repository;

        RedisBackedTestService(RedisBackedTestRepository repository) {
            this.repository = repository;
        }

        @Cacheable(cacheNames = CacheNames.NORMAL_ITEM, keyGenerator = "redisCacheKeyGenerator")
        public TestValue normalItem(String cacheName) {
            return repository.load(cacheName);
        }

        @Cacheable(cacheNames = CacheNames.NORMAL_LIST, keyGenerator = "redisCacheKeyGenerator")
        public List<TestValue> normalList(String cacheName) {
            return List.of(repository.load(cacheName));
        }

        @Cacheable(cacheNames = CacheNames.NORMAL_PAGE, keyGenerator = "redisCacheKeyGenerator")
        public CachedPageDTO<TestValue> normalPage(String cacheName) {
            Page<TestValue> page = new PageImpl<>(List.of(repository.load(cacheName)), PageRequest.of(0, 1), 1);
            return CachedPageDTO.from(page);
        }

        @Cacheable(cacheNames = CacheNames.NORMAL_SEARCH, keyGenerator = "redisCacheKeyGenerator")
        public CachedPageDTO<TestValue> normalSearch(String cacheName) {
            Page<TestValue> page = new PageImpl<>(List.of(repository.load(cacheName)), PageRequest.of(0, 1), 1);
            return CachedPageDTO.from(page);
        }

        @Cacheable(cacheNames = CacheNames.LOV_ITEM, keyGenerator = "redisCacheKeyGenerator")
        public TestValue lovItem(String cacheName) {
            return repository.load(cacheName);
        }

        @Cacheable(cacheNames = CacheNames.LOV_LIST, keyGenerator = "redisCacheKeyGenerator")
        public List<TestValue> lovList(String cacheName) {
            return List.of(repository.load(cacheName));
        }
    }

    record TestValue(String code, String name) {
    }
}