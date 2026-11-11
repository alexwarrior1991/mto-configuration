package com.alejandro.mtoconfiguration.core.audit;

import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.CollectionEntry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractCollectionEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCollectionRecreateEvent;
import org.hibernate.event.spi.PostCollectionRecreateEventListener;
import org.hibernate.event.spi.PostCollectionUpdateEvent;
import org.hibernate.event.spi.PostCollectionUpdateEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.event.spi.PreCollectionRemoveEvent;
import org.hibernate.event.spi.PreCollectionRemoveEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Apunta en {@link EntityChangeTracker} <b>que propiedades</b> de cada fila cambia el flush.
 *
 * <p>{@link EntityListener}, el listener JPA, sabe que una fila se ha escrito, pero JPA no dice que
 * columnas: eso solo lo sabe Hibernate, que compara cada entidad con lo que cargo. Este listener
 * nativo lo recoge: las propiedades distintas de cada {@code UPDATE} y las colecciones que se
 * reescriben (las estaciones de una via viven en su tabla de union, no en su fila). Con ello,
 * {@code CopiedDataRepublisher} vuelve a publicar los perfiles, seccionadores y aisladores de una
 * via solo cuando cambia algo que su evento copia de ella.
 *
 * <p>Se registra a si mismo en la factoria de sesiones al crearse. Como el listener JPA, fuera de
 * un ambito abierto por un servicio no apunta nada.
 */
@Component
public class ChangedPropertiesListener implements PostUpdateEventListener, PostCollectionUpdateEventListener,
        PostCollectionRecreateEventListener, PreCollectionRemoveEventListener {

    public ChangedPropertiesListener(EntityManagerFactory entityManagerFactory) {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getEventListenerRegistry();

        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_COLLECTION_UPDATE, this);
        registry.appendListeners(EventType.POST_COLLECTION_RECREATE, this);
        registry.appendListeners(EventType.PRE_COLLECTION_REMOVE, this);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        int[] dirty = event.getDirtyProperties();

        if (dirty == null || dirty.length == 0) {
            return;
        }

        String[] names = event.getPersister().getPropertyNames();
        List<String> changed = new ArrayList<>(dirty.length);

        for (int index : dirty) {
            changed.add(names[index]);
        }

        EntityChangeTracker.recordChangedProperties(event.getEntity(), changed);
    }

    /** La coleccion se modifico en su sitio: filas que entran o salen de su tabla de union. */
    @Override
    public void onPostUpdateCollection(PostCollectionUpdateEvent event) {
        recordCollection(event);
    }

    /** La coleccion se sustituyo por otra: Hibernate quita la vieja y escribe la nueva. */
    @Override
    public void onPostRecreateCollection(PostCollectionRecreateEvent event) {
        recordCollection(event);
    }

    @Override
    public void onPreRemoveCollection(PreCollectionRemoveEvent event) {
        recordCollection(event);
    }

    private void recordCollection(AbstractCollectionEvent event) {
        Object owner = event.getAffectedOwnerOrNull();
        String role = role(event);

        if (owner == null || role == null) {
            return;
        }

        EntityChangeTracker.recordChangedProperties(owner, List.of(role.substring(role.lastIndexOf('.') + 1)));
    }

    /** {@code paquete.Track.stations}; de la coleccion, o de su entrada en el contexto si aun no lo sabe. */
    private String role(AbstractCollectionEvent event) {
        PersistentCollection<?> collection = event.getCollection();

        if (collection == null) {
            return null;
        }

        if (collection.getRole() != null) {
            return collection.getRole();
        }

        CollectionEntry entry = event.getSession().getPersistenceContextInternal().getCollectionEntry(collection);

        if (entry == null) {
            return null;
        }

        if (entry.getCurrentPersister() != null) {
            return entry.getCurrentPersister().getRole();
        }

        return entry.getLoadedPersister() != null ? entry.getLoadedPersister().getRole() : null;
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}
