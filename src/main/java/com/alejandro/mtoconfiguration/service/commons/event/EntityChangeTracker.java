package com.alejandro.mtoconfiguration.service.commons.event;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import org.hibernate.Hibernate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que una escritura de {@code BaseService} cambia <b>ademas</b> de su entidad raiz.
 *
 * <p>Un {@code PUT} de un padre escribe tambien a sus hijos: los crea, los modifica y los quita
 * ({@code BaseMapper.mergeCollection}), a cualquier profundidad. El servicio solo conoce la raiz, y
 * solo publicaba su evento; el perfil que desaparecia de un {@code PUT /tracks/{id}} se iba sin su
 * {@code DELETED} y el consumidor lo conservaba para siempre.
 *
 * <p>Quien sabe con certeza que filas se han escrito es Hibernate, al hacer flush: una entidad que
 * el mapper volco sin cambiar nada no genera {@code UPDATE}, y entonces tampoco tiene evento. Por
 * eso los cambios no los apunta el mapper sino {@code core.audit.EntityListener}, el listener JPA
 * de {@code BaseEntity} ({@code @PostPersist} y {@code @PostUpdate}), y solo mientras un servicio
 * tiene abierto un ambito en su hilo. Fuera de un ambito no se apunta nada: el borrado directo, el
 * republicado y lo que escriba cualquier otro camino siguen como estaban.
 *
 * <p>El servicio hace flush, publica el evento de su raiz como siempre y despues el de cada
 * entidad apuntada que no sea una raiz ya publicada ({@link #drain()}). Todo dentro de su
 * transaccion, asi que las filas del outbox se confirman o se deshacen con el cambio.
 *
 * <p>Un ambito abierto dentro de otro (un servicio que llama a otro en el mismo hilo) comparte lo
 * apuntado, y solo el de fuera lo publica: asi la raiz del de fuera, que el flush del de dentro
 * tambien escribe, no sale dos veces.
 */
public final class EntityChangeTracker {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private EntityChangeTracker() {
    }

    /** Un cambio apuntado: la entidad gestionada y lo que le paso. */
    public record Change(IEntity entity, EntityChangeOperation operation) {
    }

    /** Abre un ambito en este hilo, o se suma al que ya hubiera. Se cierra siempre, con try-with-resources. */
    public static Scope open() {
        State state = CURRENT.get();

        if (state == null) {
            state = new State();
            CURRENT.set(state);
        }

        state.depth++;

        return new Scope(state);
    }

    /**
     * Apunta un cambio, si hay un ambito abierto y la entidad tiene id. Lo llama el listener JPA
     * durante el flush; quien decide si la entidad publica evento es quien publica, no esto.
     */
    public static void record(Object entity, EntityChangeOperation operation) {
        State state = CURRENT.get();

        if (state == null || operation == null || !(entity instanceof IEntity changed) || changed.getId() == null) {
            return;
        }

        state.changes.merge(Key.of(changed), new Change(changed, operation), EntityChangeTracker::combine);
    }

    /**
     * Dos cambios de la misma fila en la misma escritura son un solo evento: un alta que luego se
     * retoca (un auto-flush a medias) sigue siendo un alta, y un borrado gana a todo.
     */
    private static Change combine(Change first, Change next) {
        if (next.operation() == EntityChangeOperation.DELETED) {
            return next;
        }

        if (first.operation() == EntityChangeOperation.UPDATED) {
            return next;
        }

        return first;
    }

    private static final class State {
        private final Map<Key, Change> changes = new LinkedHashMap<>();
        private final Set<Key> published = new HashSet<>();
        private int depth;
    }

    private record Key(Class<?> type, Long id) {
        private static Key of(IEntity entity) {
            return new Key(Hibernate.getClass(entity), entity.getId());
        }
    }

    /** La raiz de una escritura, cuyo evento publica el servicio por su cuenta. */
    public static void markPublished(IEntity entity) {
        State state = CURRENT.get();

        if (state != null && entity != null && entity.getId() != null) {
            state.published.add(Key.of(entity));
        }
    }

    /**
     * Lo apuntado hasta ahora que no es una raiz ya publicada, en el orden en que Hibernate lo
     * escribio, y lo olvida. Vacio sin ambito y dentro de un ambito anidado: publica el de fuera.
     */
    public static List<Change> drain() {
        State state = CURRENT.get();

        if (state == null || state.depth != 1) {
            return List.of();
        }

        List<Change> pending = new ArrayList<>();

        state.changes.forEach((key, change) -> {
            if (!state.published.contains(key)) {
                pending.add(change);
            }
        });
        state.changes.clear();

        return pending;
    }

    public static final class Scope implements AutoCloseable {

        private final State state;

        private Scope(State state) {
            this.state = state;
        }

        @Override
        public void close() {
            state.depth--;

            if (state.depth <= 0) {
                CURRENT.remove();
            }
        }
    }
}
