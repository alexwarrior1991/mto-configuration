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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cada test usa claves UNICAS, derivadas de un identificador de ejecucion.
 * <p>
 * Es la decision de diseno central y no es cosmetica. La version anterior compartia
 * claves y contenedor entre metodos, y el subconjunto que fallaba cambiaba de una
 * ejecucion a otra. Los intentos de arreglarlo limpiando Redis entre tests lo
 * empeoraron: Cache.clear() borra por patron, asi que arrasaba con lo que el propio
 * setUp acababa de escribir. Con claves unicas no hace falta limpiar nada y esa
 * clase entera de problemas desaparece por construccion.
 * <p>
 * Cada comprobacion separa ademas las dos mitades de "hubo acierto de cache":
 * primero mira si la entrada EXISTE en Redis tras la primera llamada (escritura) y
 * luego si la segunda llamada evita el repositorio (lectura). Antes, cuando fallaba,
 * el mensaje de Mockito no distinguia entre esas dos causas y cada fallo costaba una
 * ronda de hipotesis.
 */
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

    /** Sufijo unico por ejecucion: ningun test puede pisar la clave de otro. */
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

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

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisCacheKeyGenerator cacheKeyGenerator;

    @Autowired
    private Environment environment;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("cache.redis.allowed-subtypes[0]", () -> "java.util");
        registry.add("cache.redis.allowed-subtypes[1]", () -> "org.springframework.data.domain");
        registry.add("cache.redis.allowed-subtypes[2]", () -> "com.alejandro.mtoconfiguration");
        registry.add("cache.redis.allowed-subtypes[3]", () -> "java.lang");
        // application.yaml deja el timeout en 1s, que es lo correcto en produccion pero
        // se queda corto aqui: con la suite entera compitiendo por la maquina, un Redis
        // recien arrancado tarda a veces mas y los tests fallaban de forma intermitente.
        registry.add("spring.data.redis.timeout", () -> "5s");
    }

    /**
     * Diagnostico, y va deliberadamente el primero: comprueba por capas que la cache
     * esta bien montada, para que un fallo posterior no pueda deberse a esto.
     */
    @Test
    void shouldHaveAWorkingCacheBeforeCheckingAnyHit() {
        assertThat(cacheManager.getClass().getName())
                .as("CacheManager real (spring.cache.type resuelto = '%s')",
                        environment.getProperty("spring.cache.type", "<sin definir>"))
                .contains("Redis");

        Cache cache = cacheManager.getCache(CacheNames.NORMAL_ITEM);
        assertThat(cache).as("la cache '%s' no esta registrada", CacheNames.NORMAL_ITEM).isNotNull();
        assertThat(cache.getClass().getName()).as("Cache real").contains("Redis");

        String probeKey = "diagnostico-" + RUN_ID;
        TestValue value = new TestValue(probeKey, "value");
        cache.put(probeKey, value);

        assertThat(cache.get(probeKey))
                .as("Redis no devuelve lo que se acaba de guardar")
                .isNotNull();
        assertThat(cache.get(probeKey).get()).isEqualTo(value);

        assertThat(cacheKeyGenerator.buildKey(service, "normalItem", probeKey))
                .as("la clave cambia entre llamadas identicas")
                .isEqualTo(cacheKeyGenerator.buildKey(service, "normalItem", probeKey));

        assertThat(AopUtils.isAopProxy(service)).as("el servicio no esta proxiado").isTrue();
        assertThat(AopUtils.isAopProxy(pageCacheService)).as("PageCacheService no esta proxiado").isTrue();
    }

    @Test
    void shouldCacheNormalItem() {
        assertCaches(CacheNames.NORMAL_ITEM, "normalItem", service::normalItem);
    }

    @Test
    void shouldCacheNormalList() {
        assertCaches(CacheNames.NORMAL_LIST, "normalList", service::normalList);
    }

    @Test
    void shouldCacheNormalPage() {
        assertCaches(CacheNames.NORMAL_PAGE, "normalPage", service::normalPage);
    }

    @Test
    void shouldCacheNormalSearch() {
        assertCaches(CacheNames.NORMAL_SEARCH, "normalSearch", service::normalSearch);
    }

    @Test
    void shouldCacheLovItem() {
        assertCaches(CacheNames.LOV_ITEM, "lovItem", service::lovItem);
    }

    @Test
    void shouldCacheLovList() {
        assertCaches(CacheNames.LOV_LIST, "lovList", service::lovList);
    }

    @Test
    void shouldQueryLovTableOnlyOnceWhenResolvingTheSameCodeTwice() {
        String code = "P-" + RUN_ID;
        stubPortal(code, 7L);

        LovReferenceDTO first = lovReferenceResolver.resolveIdByCode("Portal", code, portalRepository);

        assertThat(lovCacheEntry(code))
                .as("resolveIdByCode no guardo nada: fallo la ESCRITURA")
                .isNotNull();

        LovReferenceDTO second = lovReferenceResolver.resolveIdByCode("Portal", code, portalRepository);

        assertThat(first.id()).isEqualTo(7L);
        assertThat(second.id()).isEqualTo(7L);
        verify(portalRepository, times(1)).findByCode(code);
    }

    /**
     * Regresion: un escalar desnudo no conserva su tipo al pasar por Redis. Un Long se
     * escribe sin marcador de tipo y vuelve como Integer para cualquier valor que quepa
     * en 32 bits, lo que hace saltar ClassCastException en el primer acierto de cache.
     * De ahi que se fije un id pequenio: con uno grande el fallo no se reproduce.
     */
    @Test
    void shouldPreserveLongTypeForSmallIdsComingBackFromRedis() {
        String code = "P-SMALL-" + RUN_ID;
        stubPortal(code, 7L);

        lovReferenceResolver.resolveIdByCode("Portal", code, portalRepository);
        LovReferenceDTO fromRedis = lovReferenceResolver.resolveIdByCode("Portal", code, portalRepository);

        verify(portalRepository, times(1)).findByCode(code);
        assertThat(fromRedis.id()).isInstanceOf(Long.class).isEqualTo(7L);
    }

    @Test
    void shouldValidateEvenWhenSearchResultComesFromCache() {
        searchLikeTestService.reset();
        String cacheKey = "SearchLikeTestService:search:" + RUN_ID;

        searchLikeTestService.search(cacheKey);
        searchLikeTestService.search(cacheKey);

        assertThat(searchLikeTestService.loads())
                .as("la segunda busqueda debia salir de Redis")
                .isEqualTo(1);
        assertThat(searchLikeTestService.validations())
                .as("la validacion vive FUERA de la cache: debe correr tambien en el acierto")
                .isEqualTo(2);
    }

    @Test
    void shouldRebuildEquivalentPageAfterReadingItBackFromRedis() {
        assertPageRoundTrip(CacheNames.NORMAL_PAGE, "RoundTrip:findAll:" + RUN_ID, true);
        assertPageRoundTrip(CacheNames.NORMAL_SEARCH, "RoundTrip:search:" + RUN_ID, false);
    }

    /**
     * Separa las dos mitades de "hubo acierto": si falla la primera asercion, el
     * @Cacheable no llego a escribir; si falla la ultima, escribio pero no sirvio.
     */
    private <T> void assertCaches(String cacheName, String methodName, Function<String, T> cachedCall) {
        String key = methodName + "-" + RUN_ID;
        when(repository.load(key)).thenReturn(new TestValue(key, "value"));

        T first = cachedCall.apply(key);
        assertThat(first).as("la primera llamada no devolvio nada").isNotNull();

        String cacheKey = cacheKeyGenerator.buildKey(service, methodName, key);
        assertThat(entry(cacheName, cacheKey))
                .as("@Cacheable no guardo nada en '%s' bajo '%s': fallo la ESCRITURA", cacheName, cacheKey)
                .isNotNull();

        T second = cachedCall.apply(key);

        assertThat(second).as("lo servido desde Redis no coincide con lo original").isEqualTo(first);
        verify(portalRepository, never()).findByCode(key);
        verify(repository, times(1)).load(key);
    }

    private void assertPageRoundTrip(String cacheName, String cacheKey, boolean pageCache) {
        Page<TestValue> original = samplePage();
        AtomicInteger loads = new AtomicInteger();
        Supplier<Page<TestValue>> loader = () -> {
            loads.incrementAndGet();
            return original;
        };

        Page<TestValue> first = pageCache
                ? pageCacheService.getPage(cacheKey, loader).toPage()
                : pageCacheService.getSearch(cacheKey, loader).toPage();

        assertThat(entry(cacheName, cacheKey))
                .as("PageCacheService no guardo nada en '%s': fallo la ESCRITURA", cacheName)
                .isNotNull();

        Page<TestValue> fromRedis = pageCache
                ? pageCacheService.getPage(cacheKey, loader).toPage()
                : pageCacheService.getSearch(cacheKey, loader).toPage();

        assertThat(loads.get())
                .as("el segundo acceso a '%s' debia salir de Redis: fallo la LECTURA", cacheKey)
                .isEqualTo(1);

        assertThat(first.getContent()).isEqualTo(original.getContent());
        assertThat(fromRedis.getContent()).isEqualTo(original.getContent());
        assertThat(fromRedis.getNumber()).isEqualTo(original.getNumber());
        assertThat(fromRedis.getSize()).isEqualTo(original.getSize());
        assertThat(fromRedis.getTotalElements()).isEqualTo(original.getTotalElements());
        assertThat(fromRedis.getSort()).isEqualTo(original.getSort());
    }

    private Cache.ValueWrapper entry(String cacheName, String cacheKey) {
        Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).as("la cache '%s' no esta registrada", cacheName).isNotNull();
        return cache.get(cacheKey);
    }

    private Cache.ValueWrapper lovCacheEntry(String code) {
        return entry(CacheNames.LOV_ITEM, "lovIdByCode:Portal:" + code);
    }

    private void stubPortal(String code, long id) {
        Portal portal = Mockito.mock(Portal.class);
        when(portal.getId()).thenReturn(id);
        when(portalRepository.findByCode(code)).thenReturn(portal);
    }

    private static Page<TestValue> samplePage() {
        List<TestValue> content = List.of(new TestValue("A-1", "first"), new TestValue("A-2", "second"));
        return new PageImpl<>(content, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "code")), 5);
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
            // ArrayList y no List.of: las listas inmutables del JDK son finales y no
            // sobreviven al viaje por Redis. Ver el javadoc de MasterDataService.getListAndMap.
            return new ArrayList<>(List.of(repository.load(cacheName)));
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
            return new ArrayList<>(List.of(repository.load(cacheName)));
        }
    }

    record TestValue(String code, String name) {
    }
}