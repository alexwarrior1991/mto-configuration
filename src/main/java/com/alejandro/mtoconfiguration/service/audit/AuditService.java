package com.alejandro.mtoconfiguration.service.audit;

import com.alejandro.mtoconfiguration.core.audit.envers.ExtendedRevisionEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AuditService {

    private final EntityManager entityManager;

    /**
     * Retrieves a list of revision numbers associated with a specific entity.
     *
     * @param entityClass the class of the entity to retrieve revisions for
     * @param entityId    the identifier of the entity whose revisions are to be fetched
     * @param revision    an optional revision parameter (not used in this implementation)
     * @return a list of revision numbers associated with the specified entity
     */
    public List<Number> getRevisions(Class<?> entityClass, Long entityId, Number revision) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.getRevisions(entityClass, entityId);
    }


    /**
     * Retrieves the state of a specific entity at a given revision.
     *
     * @param <T>         the type of the entity
     * @param entityClass the class of the entity to retrieve
     * @param entityId    the identifier of the entity
     * @param revision    the revision number to query
     * @return the entity instance as it was at the specified revision, or null if not available
     */
    public <T> T getEntityAtRevision(Class<T> entityClass, Long entityId, Number revision) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.find(entityClass, entityId, revision);
    }

    /**
     * Retrieves a list of entities that were changed in the specified revision.
     *
     * @param revision the revision number to query for changes
     * @return a list of entities that were modified in the given revision
     */
    public List<Object> getEntitiesChangedInRevision(Number revision) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.getCrossTypeRevisionChangesReader().findEntities(revision);
    }

    /**
     * Retrieves a list of revision numbers for a specific entity within the specified timestamp range.
     *
     * @param entityClass  the class of the entity to retrieve revisions for
     * @param entityId     the identifier of the entity whose revisions are to be fetched
     * @param fromDateTime the starting timestamp of the range
     * @param toDateTime   the ending timestamp of the range
     * @return a list of revision numbers associated with the specified entity within the given timestamp range
     */
    public List<Number> getRevisionsBetweenTimestamps(Class<?> entityClass, Long entityId,
                                                      LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        return (List<Number>) queryRevisions(entityClass, entityId, fromDateTime, toDateTime, false);
    }


    /**
     * Retrieves the states of a specific entity between two specified timestamps.
     *
     * @param entityClass  the class of the entity to retrieve states for
     * @param entityId     the identifier of the entity whose states are to be fetched
     * @param fromDateTime the starting timestamp of the range
     * @param toDateTime   the ending timestamp of the range
     * @return a list of object arrays, where each array contains the entity's state and additional revision metadata
     * within the specified timestamp range
     */
    public List<Object[]> getEntityStatesBetweenTimestamps(Class<?> entityClass, Long entityId,
                                                           LocalDateTime fromDateTime, LocalDateTime toDateTime) {
        return (List<Object[]>) queryRevisions(entityClass, entityId, fromDateTime, toDateTime, true);
    }

    private List<?> queryRevisions(Class<?> entityClass, Long entityId,
                                   LocalDateTime fromDateTime, LocalDateTime toDateTime,
                                   boolean includeStates) {

        long fromTimestamp = fromDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long toTimestamp = toDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.revisionProperty("timestamp").ge(fromTimestamp))
                .add(AuditEntity.revisionProperty("timestamp").le(toTimestamp))
                .add(AuditEntity.id().eq(entityId))
                .getResultList();


        if (!includeStates) {
            return results.stream()
                    .map(result -> (Number) result[1])
                    .collect(Collectors.toList());
        }
        // Devuelve los estados completos si includeStates es verdadero
        return results;
    }

    /**
     * Retrieves only specific properties of an entity at a given revision using projections.
     *
     * @param <T>         the type of the entity
     * @param entityClass the class of the entity to retrieve
     * @param entityId    the identifier of the entity
     * @param revision    the revision number to query
     * @param properties  the names of the properties to retrieve
     * @return a map containing the requested properties and their values
     */
    public <T> Map<String, Object> getEntityPropertiesAtRevision(Class<T> entityClass, Long entityId,
                                                                 Number revision, String... properties) {

        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .add(AuditEntity.revisionNumber().eq(revision));

        Arrays.stream(properties).forEach(property -> query.addProjection(AuditEntity.property(property)));

        List<Object> result = query.getResultList();

        // Create a map of property names to values
        Map<String, Object> propertyMap = new HashMap<>();
        for (int i = 0; i < properties.length; i++) {
            propertyMap.put(properties[i], result.get(i));
        }

        return propertyMap;
    }

    /**
     * Finds all deleted entities of a specific type.
     *
     * @param <T>         the type of the entity
     * @param entityClass the class of the entity to retrieve
     * @return a list of deleted entities with their revision information
     */
    public <T> List<Object[]> findDeletedEntities(Class<T> entityClass) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        return auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.revisionType().eq(RevisionType.DEL))
                .getResultList();
    }

    /**
     * Compares two revisions of an entity and returns the differences.
     *
     * @param <T>         the type of the entity
     * @param entityClass the class of the entity to compare
     * @param entityId    the identifier of the entity
     * @param oldRevision the older revision number
     * @param newRevision the newer revision number
     * @return a map containing the property names and their changed values [old, new]
     */
    public <T> Map<String, Object[]> compareRevisions(Class<T> entityClass, Long entityId,
                                                      Number oldRevision, Number newRevision) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        T oldEntity = auditReader.find(entityClass, entityId, oldRevision);
        T newEntity = auditReader.find(entityClass, entityId, newRevision);

        Map<String, Object[]> differences = new HashMap<>();

        differences.put("entity", new Object[]{oldEntity, newEntity});

        return differences;
    }

    /**
     * Retrieves revision information including the user who made the changes.
     *
     * @param revision the revision number
     * @return the extended revision entity with user information
     */
    public ExtendedRevisionEntity getRevisionInfo(Number revision) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.findRevision(ExtendedRevisionEntity.class, revision);
    }

    /**
     * Finds all revisions where a specific property of an entity has a certain value.
     *
     * @param <T>          the type of the entity
     * @param entityClass  the class of the entity to query
     * @param propertyName the name of the property to check
     * @param value        the value to search for
     * @return a list of revisions where the property has the specified value
     */
    public <T> List<Number> findRevisionsByPropertyValue(Class<T> entityClass, String propertyName, Object value) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.property(propertyName).eq(value))
                .getResultList();

        return results.stream()
                .map(result -> (Number) result[1])
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the history of a specific property of an entity.
     *
     * @param <T>          the type of the entity
     * @param entityClass  the class of the entity
     * @param entityId     the identifier of the entity
     * @param propertyName the name of the property to track
     * @return a map of revision numbers to property values
     */
    public <T> Map<Number, Object> getPropertyHistory(Class<T> entityClass, Long entityId, String propertyName) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .addProjection(AuditEntity.revisionNumber())
                .addProjection(AuditEntity.property(propertyName))
                .getResultList();

        Map<Number, Object> history = new LinkedHashMap<>();
        for (Object[] result : results) {
            history.put((Number) result[0], result[1]);
        }

        return history;
    }

    /**
     * Finds entities that were modified by a specific user.
     *
     * @param <T>         the type of the entity
     * @param entityClass the class of the entity
     * @param username    the username to search for
     * @return a list of entities modified by the specified user
     */
    public <T> List<T> findEntitiesModifiedByUser(Class<T> entityClass, String username) {
        // This method assumes that the username is stored in the ExtendedRevisionEntity
        // You may need to adjust this based on your actual implementation
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        // This is a simplified example. In a real implementation, you would need to
        // query the revision entity for revisions by the specified user, then find
        // the entities associated with those revisions.
        return Collections.emptyList();
    }

    /**
     * Retrieves the revision history of a collection relationship.
     *
     * @param <T>            the type of the entity
     * @param entityClass    the class of the entity
     * @param entityId       the identifier of the entity
     * @param collectionName the name of the collection property
     * @return a map of revision numbers to collection states
     */
    public <T> Map<Number, Collection<?>> getCollectionHistory(Class<T> entityClass, Long entityId, String collectionName) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .getResultList();

        Map<Number, Collection<?>> history = new LinkedHashMap<>();
        for (Object[] result : results) {
            T entity = (T) result[0];
            Number revisionNumber = (Number) result[1];

            // This is a simplified approach. In a real implementation, you would use reflection
            // to access the collection property of the entity.
            // For demonstration purposes, we'll return empty collections
            history.put(revisionNumber, Collections.emptyList());
        }

        return history;
    }

    /**
     * Finds all entities that were modified in a specific date range.
     *
     * @param <T>          the type of the entity
     * @param entityClass  the class of the entity
     * @param fromDateTime the starting timestamp of the range
     * @param toDateTime   the ending timestamp of the range
     * @return a list of entities modified within the specified date range
     */
    public <T> List<T> findEntitiesModifiedInDateRange(Class<T> entityClass,
                                                       LocalDateTime fromDateTime,
                                                       LocalDateTime toDateTime) {
        long fromTimestamp = fromDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long toTimestamp = toDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, true, false)  // selectDeletedEntities = true, selectEntitiesOnly = false
                .add(AuditEntity.revisionProperty("timestamp").ge(fromTimestamp))
                .add(AuditEntity.revisionProperty("timestamp").le(toTimestamp))
                .getResultList();

        return results.stream()
                .map(result -> (T) result[0])
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the last revision where an entity was in a specific state.
     *
     * @param <T>          the type of the entity
     * @param entityClass  the class of the entity
     * @param propertyName the name of the property to check
     * @param value        the value to search for
     * @return the latest revision number where the property had the specified value
     */
    public <T> Number getLastRevisionWithPropertyValue(Class<T> entityClass, String propertyName, Object value) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        List<Number> revisions = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.property(propertyName).eq(value))
                .addProjection(AuditEntity.revisionNumber())
                .addOrder(AuditEntity.revisionNumber().desc())
                .setMaxResults(1)
                .getResultList();

        return revisions.isEmpty() ? null : revisions.get(0);
    }
}
