package com.alejandro.mtoconfiguration.repository.jpa.commons;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lista blanca de ordenacion.
 *
 * <p>{@code sortBy} es entrada del usuario y acaba convertido en una ruta de la consulta. Sin lista
 * blanca se puede ordenar por cualquier columna interna, o tumbar la consulta pidiendo una ruta que
 * no existe. Por eso lo que se prueba aqui no es que ordene, es que <b>rechace</b>.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SortPathsTest {

    private static final Set<String> ALLOWED = Set.of("name", "kp", "track.name");

    @Mock
    private Root<Object> root;
    @Mock
    private Path<Object> path;
    @Mock
    private Join<Object, Object> join;
    @Mock
    private Attribute<Object, Object> attribute;

    @Test
    @DisplayName("una propiedad de la lista blanca resuelve como ruta directa de la raiz")
    void propiedadPermitida() {
        when(root.get("name")).thenReturn(path);

        assertThat(SortPaths.<Object, Object>resolve(root, "name", ALLOWED)).isSameAs(path);
    }

    @Test
    @DisplayName("una propiedad fuera de la lista blanca devuelve null y no toca la consulta")
    void propiedadNoPermitida() {
        assertThat(SortPaths.<Object, Object>resolve(root, "createUser", ALLOWED)).isNull();
        assertThat(SortPaths.<Object, Object>resolve(root, "deleted", ALLOWED)).isNull();

        verifyNoInteractions(root);
    }

    @Test
    @DisplayName("un sortBy nulo o en blanco cae al orden por defecto")
    void sortByVacio() {
        assertThat(SortPaths.<Object, Object>resolve(root, null, ALLOWED)).isNull();
        assertThat(SortPaths.<Object, Object>resolve(root, "", ALLOWED)).isNull();
        assertThat(SortPaths.<Object, Object>resolve(root, "   ", ALLOWED)).isNull();

        verifyNoInteractions(root);
    }

    @Test
    @DisplayName("una ruta anidada permitida crea el join de la asociacion")
    void rutaAnidada() {
        when(root.getJoins()).thenReturn(new LinkedHashSet<>());
        when(root.join("track", JoinType.LEFT)).thenReturn(join);
        when(join.get("name")).thenReturn(path);

        assertThat(SortPaths.<Object, Object>resolve(root, "track.name", ALLOWED)).isSameAs(path);

        verify(root).join("track", JoinType.LEFT);
    }

    @Test
    @DisplayName("si la asociacion ya esta unida se reutiliza el join, no se duplica")
    void joinReutilizado() {
        // Un join duplicado no da error: multiplica las filas del resultado en silencio.
        when(attribute.getName()).thenReturn("track");
        org.mockito.Mockito.doReturn(attribute).when(join).getAttribute();
        when(join.get("name")).thenReturn(path);
        when(root.getJoins()).thenReturn(new LinkedHashSet<>(Set.of(join)));

        assertThat(SortPaths.<Object, Object>resolve(root, "track.name", ALLOWED)).isSameAs(path);

        verify(root, never()).join("track", JoinType.LEFT);
    }

    @Test
    @DisplayName("una ruta anidada que no esta en la lista blanca no crea ningun join")
    void rutaAnidadaNoPermitida() {
        assertThat(SortPaths.<Object, Object>resolve(root, "station.secretColumn", ALLOWED)).isNull();

        verifyNoInteractions(root);
    }
}
