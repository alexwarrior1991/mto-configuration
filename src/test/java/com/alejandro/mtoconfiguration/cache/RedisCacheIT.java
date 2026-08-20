package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.CacheNames;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import com.alejandro.mtoconfiguration.entity.lov.Portal;
import com.alejandro.mtoconfiguration.model.commons.CachedPageDTO;
import com.alejandro.mtoconfiguration.model.commons.LovReferenceDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PortalRepository;
import com.alejandro.mtoconfiguration.service.commons.LovReferenceResolver;
import com.alejandro.mtoconfiguration.service.commons.PageCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
// Con @SpringBootTest(classes = ...) Spring Boot NO aplica sus autoconfiguraciones,
// asi que nadie crearia el RedisConnectionFactory ni el RedisCacheManager y
// @EnableCaching fallaria con "No qualifying bean of type CacheManager".
// Se importan solo las dos que hacen falta, en vez de arrancar la aplicacion entera.
@ImportAutoConfiguration({DataRedisAutoConfiguration.class, CacheAutoConfiguration.class})
@SpringBootTest(classes = {
        RedisCacheConfig.class,
        RedisCacheKeyGenerator.class,
        LovReferenceResolver.class,
        PageCacheService.class,
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

    @Autowired
    private LovReferenceResolver lovReferenceResolver;

    @Autowired
    private PortalRepository portalRepository;

    @Autowired
    private PageCacheService pageCacheService;

    @Autowired
    private SearchLikeTestService searchLikeTestService;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("cache.redis.allowed-subtypes[0]", () -> "java.util");
        registry.add("cache.redis.allowed-subtypes[1]", () -> "org.springframework.data.domain");
        registry.add("cache.redis.allowed-subtypes[2]", () -> "com.alejandro.mtoconfiguration");
        registry.add("cache.redis.allowed-subtypes[3]", () -> "java.lang");
    }

    @BeforeEach
    void setUp() {
        reset(repository, portalRepository);
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

    @Test
    void shouldQueryLovTableOnlyOnceWhenResolvingTheSameCodeTwice() {
        Portal portal = Mockito.mock(Portal.class);
        when(portal.getId()).thenReturn(7L);
        when(portalRepository.findByCode("P-1")).thenReturn(portal);

        LovReferenceDTO first = lovReferenceResolver.resolveIdByCode("Portal", "P-1", portalRepository);
        LovReferenceDTO second = lovReferenceResolver.resolveIdByCode("Portal", "P-1", portalRepository);

        assertThat(first.id()).isEqualTo(7L);
        assertThat(second.id()).isEqualTo(7L);
        verify(portalRepository, times(1)).findByCode("P-1");
    }

    /**
     * Regresion: un escalar desnudo no conserva su tipo al pasar por Redis. El
     * serializador usa default typing sobre tipos no finales, asi que un Long se
     * escribe como "7" sin marcador y vuelve como Integer para cualquier valor que
     * quepa en 32 bits, haciendo saltar ClassCastException en el primer acierto de
     * cache. Con un id pequenio este test falla si alguien vuelve a cachear el Long
     * directamente; con uno grande el fallo no se reproduce, de ahi que se fije 7L.
     */
    @Test
    void shouldPreserveLongTypeForSmallIdsComingBackFromRedis() {
        Portal portal = Mockito.mock(Portal.class);
        when(portal.getId()).thenReturn(7L);
        when(portalRepository.findByCode("P-SMALL")).thenReturn(portal);

        lovReferenceResolver.resolveIdByCode("Portal", "P-SMALL", portalRepository);

        // Segunda llamada: el valor sale de Redis, no del repositorio
        LovReferenceDTO fromRedis = lovReferenceResolver.resolveIdByCode("Portal", "P-SMALL", portalRepository);

        verify(portalRepository, times(1)).findByCode("P-SMALL");
        assertThat(fromRedis.id())
                .isInstanceOf(Long.class)
                .isEqualTo(7L);
    }

    @Test
    void shouldValidateEvenWhenSearchResultComesFromCache() {
        searchLikeTestService.reset();

        Page<TestValue> first = searchLikeTestService.search("SearchLikeTestService:search:validated");
        Page<TestValue> second = searchLikeTestService.search("SearchLikeTestService:search:validated");

        assertThat(second.getContent()).isEqualTo(first.getContent());
        assertThat(searchLikeTestService.loads()).isEqualTo(1);
        assertThat(searchLikeTestService.validations()).isEqualTo(2);
    }

    @Test
    void shouldRebuildEquivalentPageAfterReadingItBackFromRedis() {
        Page<TestValue> original = samplePage();

        assertPageRoundTrip("RoundTripService:findAll:page", original, true);
        assertPageRoundTrip("RoundTripService:search:page", original, false);
    }

    private void assertPageRoundTrip(String cacheKey, Page<TestValue> original, boolean pageCache) {
        Supplier<Page<TestValue>> loader = () -> original;

        Page<TestValue> first = pageCache
                ? pageCacheService.getPage(cacheKey, loader).toPage()
                : pageCacheService.getSearch(cacheKey, loader).toPage();

        Page<TestValue> fromRedis = pageCache
                ? pageCacheService.getPage(cacheKey, loader).toPage()
                : pageCacheService.getSearch(cacheKey, loader).toPage();

        assertThat(first.getContent()).isEqualTo(original.getContent());
        assertThat(fromRedis.getContent()).isEqualTo(original.getContent());
        assertThat(fromRedis.getNumber()).isEqualTo(original.getNumber());
        assertThat(fromRedis.getSize()).isEqualTo(original.getSize());
        assertThat(fromRedis.getTotalElements()).isEqualTo(original.getTotalElements());
        assertThat(fromRedis.getSort()).isEqualTo(original.getSort());
    }

    private static Page<TestValue> samplePage() {
        List<TestValue> content = List.of(new TestValue("A-1", "first"), new TestValue("A-2", "second"));
        PageRequest pageRequest = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "code"));

        return new PageImpl<>(content, pageRequest, 5);
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

        @Bean
        PortalRepository portalRepository() {
            return Mockito.mock(PortalRepository.class);
        }

        @Bean
        SearchLikeTestService searchLikeTestService(PageCacheService pageCacheService) {
            return new SearchLikeTestService(pageCacheService);
        }
    }

    /**
     * Réplica del patrón de BaseService.search: la validación queda fuera del bean
     * cacheado, así que debe ejecutarse también cuando el resultado sale de Redis.
     */
    static class SearchLikeTestService {

        private final PageCacheService pageCacheService;
        private final AtomicInteger validations = new AtomicInteger();
        private final AtomicInteger loads = new AtomicInteger();

        SearchLikeTestService(PageCacheService pageCacheService) {
            this.pageCacheService = pageCacheService;
        }

        Page<TestValue> search(String cacheKey) {
            validations.incrementAndGet();

            return pageCacheService.getSearch(cacheKey, () -> {
                loads.incrementAndGet();
                return new PageImpl<>(List.of(new TestValue("S-1", "searched")), PageRequest.of(0, 1), 1);
            }).toPage();
        }

        int validations() {
            return validations.get();
        }

        int loads() {
            return loads.get();
        }

        void reset() {
            validations.set(0);
            loads.set(0);
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