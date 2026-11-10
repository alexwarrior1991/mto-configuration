package com.alejandro.mtoconfiguration.service.commons.event;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker.Change;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que una escritura apunta ademas de su raiz, que es de donde salen los eventos de los hijos
 * escritos dentro de su padre. Lo que se fija aqui es lo que el compilador no ve: que sin ambito
 * no se apunta nada, que una fila es un solo evento y que la raiz no sale dos veces.
 */
@DisplayName("EntityChangeTracker")
class EntityChangeTrackerTest {

    private static class Row extends CRUDEntity {
        Row(Long id) {
            this.id = id;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    private static class OtherRow extends Row {
        OtherRow(Long id) {
            super(id);
        }
    }

    @Test
    @DisplayName("sin un ambito abierto no se apunta nada, y al cerrarlo se olvida todo")
    void sinAmbito() {
        EntityChangeTracker.record(new Row(1L), EntityChangeOperation.UPDATED);
        assertThat(EntityChangeTracker.drain()).isEmpty();

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            EntityChangeTracker.record(new Row(1L), EntityChangeOperation.UPDATED);
        }

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            assertThat(EntityChangeTracker.drain()).isEmpty();
        }
    }

    @Test
    @DisplayName("lo apuntado sale en su orden, una vez, y sin la raiz que el servicio ya publico")
    void drenaSinLaRaiz() {
        Row raiz = new Row(1L);
        Row creado = new Row(2L);
        Row borrado = new OtherRow(2L);   // mismo id, otra entidad: es otra fila

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            EntityChangeTracker.record(creado, EntityChangeOperation.CREATED);
            EntityChangeTracker.record(raiz, EntityChangeOperation.UPDATED);
            EntityChangeTracker.record(borrado, EntityChangeOperation.DELETED);
            EntityChangeTracker.markPublished(raiz);

            assertThat(EntityChangeTracker.drain()).containsExactly(
                    new Change(creado, EntityChangeOperation.CREATED),
                    new Change(borrado, EntityChangeOperation.DELETED));
            assertThat(EntityChangeTracker.drain()).as("ya esta publicado").isEmpty();
        }
    }

    @Test
    @DisplayName("una entidad sin id, o que no es una entidad, no se apunta")
    void sinId() {
        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            EntityChangeTracker.record(new Row(null), EntityChangeOperation.CREATED);
            EntityChangeTracker.record("no es una entidad", EntityChangeOperation.CREATED);
            EntityChangeTracker.record(new Row(3L), null);

            assertThat(EntityChangeTracker.drain()).isEmpty();
        }
    }

    @Test
    @DisplayName("dos escrituras de la misma fila son un evento: el alta sigue siendo alta y el borrado gana")
    void unaFilaUnEvento() {
        Row alta = new Row(1L);
        Row modificada = new Row(2L);
        Row borrada = new Row(3L);

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            EntityChangeTracker.record(alta, EntityChangeOperation.CREATED);
            EntityChangeTracker.record(alta, EntityChangeOperation.UPDATED);
            EntityChangeTracker.record(modificada, EntityChangeOperation.UPDATED);
            EntityChangeTracker.record(modificada, EntityChangeOperation.UPDATED);
            EntityChangeTracker.record(borrada, EntityChangeOperation.UPDATED);
            EntityChangeTracker.record(borrada, EntityChangeOperation.DELETED);

            assertThat(EntityChangeTracker.drain()).containsExactly(
                    new Change(alta, EntityChangeOperation.CREATED),
                    new Change(modificada, EntityChangeOperation.UPDATED),
                    new Change(borrada, EntityChangeOperation.DELETED));
        }
    }

    @Test
    @DisplayName("un ambito dentro de otro no publica: lo hace el de fuera, con lo de los dos")
    void ambitoAnidado() {
        Row deFuera = new Row(1L);
        Row deDentro = new Row(2L);

        try (EntityChangeTracker.Scope fuera = EntityChangeTracker.open()) {
            EntityChangeTracker.record(deFuera, EntityChangeOperation.UPDATED);

            try (EntityChangeTracker.Scope dentro = EntityChangeTracker.open()) {
                EntityChangeTracker.record(deDentro, EntityChangeOperation.CREATED);
                assertThat(EntityChangeTracker.drain()).isEmpty();
            }

            assertThat(EntityChangeTracker.drain()).containsExactly(
                    new Change(deFuera, EntityChangeOperation.UPDATED),
                    new Change(deDentro, EntityChangeOperation.CREATED));
        }
    }

    @Test
    @DisplayName("el ambito es del hilo: otra peticion no ve lo de esta")
    void esDelHilo() {
        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            CompletableFuture.runAsync(() -> EntityChangeTracker.record(new Row(9L), EntityChangeOperation.UPDATED))
                    .join();

            assertThat(EntityChangeTracker.drain()).isEmpty();
        }
    }
}
