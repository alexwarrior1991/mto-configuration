package com.alejandro.mtoconfiguration.cache;

import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheConfig;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import com.alejandro.mtoconfiguration.model.commons.PageableDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La clave de caché debe depender de lo que la búsqueda significa, no de cómo el
 * cliente escribió el JSON. Dos peticiones equivalentes tienen que compartir
 * entrada en Redis, y dos peticiones distintas no deben colisionar jamás.
 */
class RedisCacheKeyGeneratorTest {

    private RedisCacheKeyGenerator keyGenerator;
    private Method searchMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        keyGenerator = new RedisCacheKeyGenerator(new RedisCacheConfig().redisCacheKeyObjectMapper());
        searchMethod = FakeService.class.getMethod("search", SearchRequestDTO.class);
    }

    // --- Estabilidad: peticiones equivalentes, misma clave ---

    @Test
    void shouldProduceSameKeyWhenFilterFieldsComeInDifferentOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "X");
        first.put("enabled", true);
        first.put("kp", 12);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("kp", 12);
        second.put("enabled", true);
        second.put("name", "X");

        assertThat(keyFor(first)).isEqualTo(keyFor(second));
    }

    @Test
    void shouldProduceSameKeyWhenInListComesInDifferentOrder() {
        // [1,2,3] y [3,1,2] son la misma clausula IN
        assertThat(keyFor(Map.of("ids", List.of(1, 2, 3))))
                .isEqualTo(keyFor(Map.of("ids", List.of(3, 1, 2))));
    }

    @Test
    void shouldNormalizeNestedListsInsideFilters() {
        Map<String, Object> first = Map.of("grupo", Map.of("ids", List.of("b", "a")));
        Map<String, Object> second = Map.of("grupo", Map.of("ids", List.of("a", "b")));

        assertThat(keyFor(first)).isEqualTo(keyFor(second));
    }

    // --- Sensibilidad: peticiones distintas, claves distintas ---

    @Test
    void shouldProduceDifferentKeysWhenSortOrderChanges() {
        // sortBy y sortDirection son listas PARALELAS y el orden de los criterios
        // cambia el resultado: reordenarlas daria la misma clave a dos busquedas
        // que devuelven paginas distintas.
        SearchRequestDTO byNameThenKp = request(Map.of("name", "X"),
                List.of("name", "kp"), List.of("ASC", "DESC"));
        SearchRequestDTO byKpThenName = request(Map.of("name", "X"),
                List.of("kp", "name"), List.of("DESC", "ASC"));

        assertThat(keyFor(byNameThenKp)).isNotEqualTo(keyFor(byKpThenName));
    }

    @Test
    void shouldProduceDifferentKeysForDifferentFilterValues() {
        assertThat(keyFor(Map.of("name", "X"))).isNotEqualTo(keyFor(Map.of("name", "Y")));
    }

    @Test
    void shouldProduceDifferentKeysForDifferentPages() {
        SearchRequestDTO page0 = request(Map.of("name", "X"), List.of("name"), List.of("ASC"));
        page0.getPageable().setPage(0);

        SearchRequestDTO page1 = request(Map.of("name", "X"), List.of("name"), List.of("ASC"));
        page1.getPageable().setPage(1);

        assertThat(keyFor(page0)).isNotEqualTo(keyFor(page1));
    }

    // --- Forma de la clave ---

    @Test
    void shouldPrefixKeyWithServiceAndMethodSoEvictionByPatternWorks() {
        // RedisCacheEvictService borra por patron "<Servicio>:*": si el prefijo
        // cambia, la invalidacion deja de encontrar las claves.
        String key = keyFor(Map.of("name", "X"));

        assertThat(key).startsWith("FakeService:search:");
    }

    @Test
    void shouldKeepSimpleValuesReadableInsteadOfHashingThem() throws NoSuchMethodException {
        Method getById = FakeService.class.getMethod("getById", Long.class);

        Object key = keyGenerator.generate(new FakeService(), getById, 5L);

        assertThat(key).isEqualTo("FakeService:getById:5");
    }

    @Test
    void shouldNormalizePageableIntoAReadableSegment() throws NoSuchMethodException {
        Method findAll = FakeService.class.getMethod("findAll", org.springframework.data.domain.Pageable.class);

        Object key = keyGenerator.generate(new FakeService(), findAll,
                PageRequest.of(2, 20, Sort.by(Sort.Direction.DESC, "name")));

        assertThat(key).isEqualTo("FakeService:findAll:page=2:size=20:sort=name,DESC");
    }

    @Test
    void shouldHandleNullParametersWithoutFailing() throws NoSuchMethodException {
        Method getById = FakeService.class.getMethod("getById", Long.class);

        Object key = keyGenerator.generate(new FakeService(), getById, new Object[]{null});

        assertThat(key).isEqualTo("FakeService:getById:null");
    }

    // --- helpers ---

    private String keyFor(Map<String, Object> filters) {
        return keyFor(request(filters, List.of("name"), List.of("ASC")));
    }

    private String keyFor(SearchRequestDTO request) {
        return keyGenerator.generate(new FakeService(), searchMethod, request).toString();
    }

    private SearchRequestDTO request(Map<String, Object> filters, List<String> sortBy, List<String> sortDirection) {
        PageableDTO pageable = new PageableDTO();
        pageable.setPage(0);
        pageable.setSize(20);
        pageable.setSortBy(sortBy);
        pageable.setSortDirection(sortDirection);

        SearchRequestDTO request = new SearchRequestDTO();
        request.setFilters(new LinkedHashMap<>(filters));
        request.setPageable(pageable);
        return request;
    }

    static class FakeService {
        public Object search(SearchRequestDTO request) {
            return null;
        }

        public Object getById(Long id) {
            return null;
        }

        public Object findAll(org.springframework.data.domain.Pageable pageable) {
            return null;
        }
    }
}
