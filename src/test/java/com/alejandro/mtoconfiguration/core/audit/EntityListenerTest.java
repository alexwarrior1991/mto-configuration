package com.alejandro.mtoconfiguration.core.audit;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker.Change;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El listener JPA de {@code BaseEntity}: que apunta, y con que operacion, lo que Hibernate escribe. */
@DisplayName("EntityListener")
class EntityListenerTest {

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

    private final EntityListener listener = new EntityListener();

    @Test
    @DisplayName("un INSERT es un alta, un UPDATE una modificacion, y el UPDATE que deja deleted = true, un borrado")
    void operaciones() {
        Row creada = new Row(1L);
        Row modificada = new Row(2L);
        Row borrada = new Row(3L);
        borrada.delete();

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            listener.onInsert(creada);
            listener.onUpdate(modificada);
            listener.onUpdate(borrada);

            assertThat(EntityChangeTracker.drain()).containsExactly(
                    new Change(creada, EntityChangeOperation.CREATED),
                    new Change(modificada, EntityChangeOperation.UPDATED),
                    new Change(borrada, EntityChangeOperation.DELETED));
        }
    }

    @Test
    @DisplayName("fuera de la escritura de un servicio no apunta nada")
    void sinAmbito() {
        listener.onInsert(new Row(1L));

        try (EntityChangeTracker.Scope ignored = EntityChangeTracker.open()) {
            assertThat(EntityChangeTracker.drain()).isEmpty();
        }
    }
}
