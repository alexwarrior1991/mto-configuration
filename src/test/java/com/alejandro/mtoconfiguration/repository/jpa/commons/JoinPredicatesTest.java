package com.alejandro.mtoconfiguration.repository.jpa.commons;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Predicados sobre asociaciones.
 *
 * <p>Lo que hay que proteger aqui son dos decisiones que no se ven en el sitio donde se usan:
 * que el join <b>solo</b> se cree cuando el filtro viene informado —para que una busqueda sin
 * filtros no arrastre LEFT JOIN inutiles— y que dos filtros sobre la misma asociacion
 * <b>reutilicen</b> el mismo join. Sin lo segundo, filtrar por el nombre de la estacion y buscar
 * texto libre sobre esa misma estacion genera dos JOIN a la misma tabla y con ellos un producto
 * cartesiano que duplica filas.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JoinPredicatesTest {

    @Mock
    private CriteriaBuilder cb;
    @Mock
    private From<Object, Object> from;
    @Mock
    private Join<Object, Object> join;
    @Mock
    private Path<Object> path;
    @Mock
    private Expression<String> upper;
    @Mock
    private Predicate marker;
    @Mock
    private Attribute<Object, Object> attribute;

    private final Map<String, Object> filters = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(from.getJoins()).thenReturn(new LinkedHashSet<>());
        doReturn(join).when(from).join(anyString(), any(JoinType.class));
        doReturn(path).when(join).get(anyString());
        doReturn(upper).when(cb).upper(any());
        when(cb.like(any(), anyString())).thenReturn(marker);
        when(cb.equal(any(), any(Object.class))).thenReturn(marker);
    }

    private String capturedLikePattern() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cb).like(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("like sobre la asociacion crea el join y compara en mayusculas")
    void like() {
        filters.put("stationName", "atocha");

        assertThat(JoinPredicates.like(cb, from, filters, "stationName", "station", "name"))
                .isSameAs(marker);

        verify(from).join("station", JoinType.LEFT);
        assertThat(capturedLikePattern()).isEqualTo("%ATOCHA%");
    }

    @Test
    @DisplayName("sin valor no se crea el join ni el predicado")
    void likeSinValor() {
        assertThat(JoinPredicates.like(cb, from, filters, "stationName", "station", "name")).isNull();

        filters.put("stationName", "   ");
        assertThat(JoinPredicates.like(cb, from, filters, "stationName", "station", "name")).isNull();

        verify(from, never()).join(anyString(), any(JoinType.class));
        verifyNoInteractions(cb);
    }

    @Test
    @DisplayName("eq compara la columna asociada con el valor recibido")
    void eq() {
        filters.put("cantileverId", 7L);

        assertThat(JoinPredicates.eq(cb, from, filters, "cantileverId", "cantilever", "id"))
                .isSameAs(marker);

        verify(cb).equal(path, 7L);
    }

    @Test
    @DisplayName("eq ignora un valor nulo o una cadena en blanco")
    void eqSinValor() {
        assertThat(JoinPredicates.eq(cb, from, filters, "cantileverId", "cantilever", "id")).isNull();

        filters.put("cantileverId", "   ");
        assertThat(JoinPredicates.eq(cb, from, filters, "cantileverId", "cantilever", "id")).isNull();

        verify(from, never()).join(anyString(), any(JoinType.class));
    }

    @Test
    @DisplayName("eq si acepta el valor cero, que no es lo mismo que ausente")
    void eqConCero() {
        filters.put("cantileverId", 0L);

        assertThat(JoinPredicates.eq(cb, from, filters, "cantileverId", "cantilever", "id"))
                .isSameAs(marker);
    }

    @Test
    @DisplayName("searchText lee la clave 'search' antes que 'searchText'")
    void searchTextPrecedencia() {
        filters.put("search", "atocha");
        filters.put("searchText", "chamartin");

        assertThat(JoinPredicates.searchText(cb, from, filters, "station", "name")).isSameAs(marker);
        assertThat(capturedLikePattern()).isEqualTo("%ATOCHA%");
    }

    @Test
    @DisplayName("searchText cae en 'searchText' si 'search' viene en blanco")
    void searchTextRespaldo() {
        filters.put("search", "  ");
        filters.put("searchText", "chamartin");

        assertThat(JoinPredicates.searchText(cb, from, filters, "station", "name")).isSameAs(marker);
        assertThat(capturedLikePattern()).isEqualTo("%CHAMARTIN%");
    }

    @Test
    @DisplayName("sin texto de busqueda no se crea join ni predicado")
    void searchTextSinValor() {
        assertThat(JoinPredicates.searchText(cb, from, filters, "station", "name")).isNull();

        verify(from, never()).join(anyString(), any(JoinType.class));
    }

    @Test
    @DisplayName("un join ya creado para la misma asociacion se reutiliza")
    void joinReutilizado() {
        // Es la diferencia entre un LEFT JOIN y dos: con dos, cada fila de la tabla asociada
        // multiplica la fila principal.
        when(attribute.getName()).thenReturn("station");
        doReturn(attribute).when(join).getAttribute();
        when(from.getJoins()).thenReturn(new LinkedHashSet<>(Set.of(join)));

        filters.put("stationName", "atocha");
        filters.put("searchText", "atocha");

        JoinPredicates.like(cb, from, filters, "stationName", "station", "name");
        JoinPredicates.searchText(cb, from, filters, "station", "name");

        verify(from, never()).join(anyString(), any(JoinType.class));
        verify(cb, times(2)).like(any(), anyString());
    }

    @Test
    @DisplayName("una asociacion distinta si crea su propio join")
    void asociacionDistinta() {
        when(attribute.getName()).thenReturn("station");
        doReturn(attribute).when(join).getAttribute();
        when(from.getJoins()).thenReturn(new LinkedHashSet<>(Set.of(join)));

        filters.put("functionName", "tierra");

        JoinPredicates.like(cb, from, filters, "functionName", "disconnectorFunction", "description");

        verify(from).join("disconnectorFunction", JoinType.LEFT);
    }
}
