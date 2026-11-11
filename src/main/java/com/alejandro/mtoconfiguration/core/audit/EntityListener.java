package com.alejandro.mtoconfiguration.core.audit;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;

/**
 * Listener JPA de todas las entidades ({@code BaseEntity}): apunta en {@link EntityChangeTracker}
 * cada fila que Hibernate inserta o modifica de verdad, para que el servicio que esta escribiendo
 * publique tambien el evento de los hijos que cambian dentro de la escritura de su padre.
 *
 * <p>{@code @PostUpdate} solo salta cuando hubo {@code UPDATE}: un hijo que el mapper volco sin
 * cambiar nada no se apunta, y no publica. Un {@code UPDATE} que deja la fila con
 * {@code deleted = true} es un borrado logico: una fila ya borrada no se puede cargar
 * ({@code @SQLRestriction}), asi que solo puede ser la que se acaba de borrar.
 *
 * <p>Sin un ambito abierto por un servicio, {@code record} no hace nada.
 */
public class EntityListener {

    @PostPersist
    public void onInsert(Object entity) {
        EntityChangeTracker.record(entity, EntityChangeOperation.CREATED);
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        boolean deleted = entity instanceof CRUDEntity crud && crud.isDeleted();

        EntityChangeTracker.record(entity, deleted ? EntityChangeOperation.DELETED : EntityChangeOperation.UPDATED);
    }
}
