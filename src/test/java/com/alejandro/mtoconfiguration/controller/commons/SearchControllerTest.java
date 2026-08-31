package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.service.commons.BaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code withFilters} decide si una peticion de busqueda trae filtros de verdad.
 *
 * <p>Es logica pura y con muchas ramas —null, mapa vacio, cadena en blanco, numero, lista vacia—,
 * y de su respuesta depende que una busqueda se resuelva con un filtro o devuelva la tabla entera.
 * Un {@code false} de mas es una consulta sin {@code where}.</p>
 */
class SearchControllerTest {

    /** Implementacion minima: {@code withFilters} no toca el servicio, asi que puede ser nulo. */
    private final SearchController<BaseDTO, IEntity> controller = () -> null;

    private static SearchRequestDTO withFilters(Map<String, Object> filters) {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setFilters(filters);
        return request;
    }

    @Test
    @DisplayName("una peticion nula, sin mapa de filtros o con el mapa vacio no tiene filtros")
    void sinFiltros() {
        assertThat(controller.withFilters(null)).isFalse();
        assertThat(controller.withFilters(new SearchRequestDTO())).isFalse();
        assertThat(controller.withFilters(withFilters(Map.of()))).isFalse();
    }

    @Test
    @DisplayName("una cadena vacia o en blanco no cuenta como filtro")
    void cadenasEnBlanco() {
        assertThat(controller.withFilters(withFilters(Map.of("name", "")))).isFalse();
        assertThat(controller.withFilters(withFilters(Map.of("name", "   ")))).isFalse();
    }

    @Test
    @DisplayName("una cadena con contenido si cuenta como filtro")
    void cadenaConContenido() {
        assertThat(controller.withFilters(withFilters(Map.of("name", "via")))).isTrue();
    }

    @Test
    @DisplayName("un Long cuenta como filtro aunque sea cero")
    void numeros() {
        assertThat(controller.withFilters(withFilters(Map.of("trackId", 0L)))).isTrue();
        assertThat(controller.withFilters(withFilters(Map.of("trackId", 7L)))).isTrue();
    }

    @Test
    @DisplayName("una lista vacia no cuenta como filtro y una con elementos si")
    void listas() {
        assertThat(controller.withFilters(withFilters(Map.of("ids", List.of())))).isFalse();
        assertThat(controller.withFilters(withFilters(Map.of("ids", List.of(1L))))).isTrue();
    }

    @Test
    @DisplayName("un tipo no contemplado (Integer, Boolean) no cuenta como filtro")
    void tiposNoContemplados() {
        // Solo se reconocen String, Long y List. Un Integer que llegue de un JSON pequeño se
        // ignora en silencio: queda escrito aqui para que el dia que se arregle se vea el cambio.
        assertThat(controller.withFilters(withFilters(Map.of("trackId", 7)))).isFalse();
        assertThat(controller.withFilters(withFilters(Map.of("enabled", true)))).isFalse();
    }

    @Test
    @DisplayName("basta con que UNO de los filtros tenga valor")
    void bastaConUno() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("name", "");
        filters.put("trackId", 3L);

        assertThat(controller.withFilters(withFilters(filters))).isTrue();
    }

    @Test
    @DisplayName("un valor nulo dentro del mapa no rompe la comprobacion")
    void valorNulo() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("name", null);

        assertThat(controller.withFilters(withFilters(filters))).isFalse();
    }
}
