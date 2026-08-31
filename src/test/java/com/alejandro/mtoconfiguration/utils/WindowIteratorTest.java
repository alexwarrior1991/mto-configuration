package com.alejandro.mtoconfiguration.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Recorrido por ventanas (keyset) que usa la exportacion de perfiles.
 *
 * <p>Es el iterador que permite recorrer una via entera sin cargarla en memoria: cada ventana se
 * pide a partir del ultimo elemento de la anterior. Dos cosas tienen que cumplirse siempre: que el
 * cursor que se pasa a la siguiente llamada sea el <b>ultimo</b> elemento de la ventana devuelta
 * —si fuese otro se saltarian o repetirian filas— y que una ventana vacia termine el recorrido.</p>
 */
class WindowIteratorTest {

    @Test
    @DisplayName("recorre todas las ventanas hasta que llega una vacia")
    void recorridoCompleto() {
        List<List<String>> windows = new ArrayList<>(List.of(
                List.of("a", "b"),
                List.of("c", "d"),
                List.of("e")));

        WindowIterator<String> iterator = new WindowIterator<>(last -> windows.isEmpty() ? List.of() : windows.removeFirst());

        List<String> visited = new ArrayList<>();
        iterator.forEachRemaining(visited::addAll);

        assertThat(visited).containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    @DisplayName("el cursor de la siguiente ventana es el ultimo elemento de la anterior")
    void cursorEsElUltimoElemento() {
        List<String> cursors = new ArrayList<>();
        List<List<String>> windows = new ArrayList<>(List.of(
                List.of("a", "b"),
                List.of("c", "d")));

        WindowIterator<String> iterator = new WindowIterator<>(last -> {
            cursors.add(last);
            return windows.isEmpty() ? List.of() : windows.removeFirst();
        });

        iterator.forEachRemaining(window -> {
        });

        // El primero es null (carga inicial); despues, el ultimo de cada ventana servida.
        assertThat(cursors).containsExactly(null, "b", "d");
    }

    @Test
    @DisplayName("si la primera consulta no devuelve nada, no hay ninguna ventana que recorrer")
    void sinResultados() {
        WindowIterator<String> iterator = new WindowIterator<>(last -> List.of());

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("una consulta que devuelve null tambien termina el recorrido")
    void resultadoNulo() {
        WindowIterator<String> iterator = new WindowIterator<>(last -> null);

        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    @DisplayName("la ventana ya cargada se sirve entera antes de pedir la siguiente")
    void unaSolaVentana() {
        List<List<String>> windows = new ArrayList<>(List.of(List.of("a")));

        WindowIterator<String> iterator = new WindowIterator<>(last -> windows.isEmpty() ? List.of() : windows.removeFirst());

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).containsExactly("a");
        assertThat(iterator.hasNext()).isFalse();
    }
}
