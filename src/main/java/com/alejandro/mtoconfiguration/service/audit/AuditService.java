package com.alejandro.mtoconfiguration.service.audit;

import com.alejandro.mtoconfiguration.core.audit.envers.AuditModifiedEntity;
import com.alejandro.mtoconfiguration.core.audit.envers.ExtendedRevisionEntity;
import com.alejandro.mtoconfiguration.model.audit.*;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Transient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class AuditService {

    private final EntityManager entityManager;

    public AuditRevisionDTO getRevisionInfo(Number revision) {
        AuditReader auditReader = getAuditReader();
        ExtendedRevisionEntity revisionEntity = auditReader.findRevision(ExtendedRevisionEntity.class, revision);

        if (revisionEntity == null) {
            return null;
        }

        return toAuditRevisionDTO(revisionEntity);
    }

    public <T> List<Number> getRevisionNumbers(Class<T> entityClass, Long entityId) {
        AuditReader auditReader = getAuditReader();
        return auditReader.getRevisions(entityClass, entityId);
    }

    public <T> T getEntityAtRevision(Class<T> entityClass, Long entityId, Number revision) {
        AuditReader auditReader = getAuditReader();
        return auditReader.find(entityClass, entityId, revision);
    }

    public <T> List<AuditEntityRevisionDTO<T>> getEntityRevisionHistory(Class<T> entityClass, Long entityId) {
        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return results.stream()
                .map(result -> toAuditEntityRevisionDTO(entityClass, result))
                .toList();
    }

    /**
     * Una pagina del historial de una fila, <b>la revision mas reciente primero</b>: lo que sirve
     * {@code GET /{recurso}/{id}/revisions}.
     *
     * <p>Pagina en la consulta de Envers, y no sobre {@link #getEntityRevisionHistory}: una fila que
     * la importacion reescribe en cada carga acumula revisiones, y traerlas todas para devolver
     * veinte es leer veinte veces de mas. El orden de {@code pageable} no se mira: el historial solo
     * tiene uno.
     *
     * <p>Con {@code selectDeletedEntities}, como el resto de consultas de esta clase: la revision
     * que dice que una entrada de catalogo se borro es justo la que no puede faltar. Una pagina sin
     * elementos y con {@code totalElements} a cero es una fila que no tiene historial; quien llama
     * decide que significa eso.
     */
    public <T> Page<AuditEntityRevisionDTO<T>> getEntityRevisionPage(
            Class<T> entityClass,
            Long entityId,
            Pageable pageable
    ) {
        AuditReader auditReader = getAuditReader();

        long total = ((Number) auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .addProjection(AuditEntity.revisionNumber().count())
                .getSingleResult()).longValue();

        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .addOrder(AuditEntity.revisionNumber().desc())
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<AuditEntityRevisionDTO<T>> content = results.stream()
                .map(result -> toAuditEntityRevisionDTO(entityClass, result))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * El historial de una fila tal como lo publica la API: cada revision con sus metadatos y con la
     * fila en la forma de su DTO.
     *
     * <p>El mapeo va aqui dentro, en la transaccion de la lectura: la entidad de una revision trae
     * sus relaciones perezosas, y con {@code open-in-view: false} recorrerlas fuera revienta.
     *
     * <p>Sin ninguna revision es un 404: toda fila escrita por la API tiene al menos la de su alta,
     * asi que cero revisiones es un id que no ha existido (o una fila sembrada con SQL por una
     * migracion, que Envers no ve). Una pagina vacia diria «no ha cambiado nunca», que no es lo
     * mismo; es tambien lo que responden {@code mto-stock} y {@code mto-maintenance}.
     *
     * <p>Una revision cuya fila no se puede reconstruir (una relacion sin auditar que apunta a una
     * fila que ya no esta) sale con {@code entity} a {@code null} en vez de tumbar la pagina: quien,
     * cuando y que operacion siguen siendo ciertos.
     *
     * @param toDto de la entidad de una revision a su DTO
     */
    public <E, D> Page<EntityRevisionDTO<D>> getEntityRevisions(
            Class<E> entityClass,
            Long entityId,
            Pageable pageable,
            Function<E, D> toDto
    ) {
        Page<AuditEntityRevisionDTO<E>> revisions = getEntityRevisionPage(entityClass, entityId, pageable);

        if (revisions.getTotalElements() == 0) {
            throw new NotFoundException(entityClass.getSimpleName() + " revision history not found with id " + entityId);
        }

        return revisions.map(revision -> new EntityRevisionDTO<>(
                RevisionMetadataDTO.of(revision),
                entityAtRevision(revision, toDto)));
    }

    private <E, D> D entityAtRevision(AuditEntityRevisionDTO<E> revision, Function<E, D> toDto) {
        if (revision.entity() == null) {
            return null;
        }

        try {
            return toDto.apply(revision.entity());
        } catch (EntityNotFoundException | ObjectNotFoundException e) {
            log.warn("La fila de la revision {} de {} no se puede reconstruir: {}",
                    revision.revision(), revision.entity().getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    public <T> List<AuditEntityRevisionDTO<T>> getEntityRevisionHistoryBetweenDates(
            Class<T> entityClass,
            Long entityId,
            LocalDateTime fromDateTime,
            LocalDateTime toDateTime
    ) {
        long fromTimestamp = toTimestamp(fromDateTime);
        long toTimestamp = toTimestamp(toDateTime);

        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .add(AuditEntity.revisionProperty("timestamp").ge(fromTimestamp))
                .add(AuditEntity.revisionProperty("timestamp").le(toTimestamp))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return results.stream()
                .map(result -> toAuditEntityRevisionDTO(entityClass, result))
                .toList();
    }

    public <T> List<AuditDifferenceDTO> compareRevisions(
            Class<T> entityClass,
            Long entityId,
            Number oldRevision,
            Number newRevision
    ) {
        AuditReader auditReader = getAuditReader();

        T oldEntity = auditReader.find(entityClass, entityId, oldRevision);
        T newEntity = auditReader.find(entityClass, entityId, newRevision);

        if (oldEntity == null && newEntity == null) {
            return List.of();
        }

        if (oldEntity == null) {
            return List.of(new AuditDifferenceDTO("entity", null, newEntity));
        }

        if (newEntity == null) {
            return List.of(new AuditDifferenceDTO("entity", oldEntity, null));
        }

        return compareObjects(entityClass, oldEntity, newEntity);
    }

    public <T> List<AuditPropertyHistoryDTO> getPropertyHistory(
            Class<T> entityClass,
            Long entityId,
            String propertyName
    ) {
        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .addProjection(AuditEntity.revisionNumber())
                .addProjection(AuditEntity.revisionProperty("timestamp"))
                .addProjection(AuditEntity.property(propertyName))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return results.stream()
                .map(result -> new AuditPropertyHistoryDTO(
                        (Number) result[0],
                        toLocalDateTime((Long) result[1]),
                        result[2]
                ))
                .toList();
    }

    public <T> List<AuditEntityRevisionDTO<T>> findEntitiesModifiedByUser(
            Class<T> entityClass,
            String username
    ) {
        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.revisionProperty("username").eq(username))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        return results.stream()
                .map(result -> toAuditEntityRevisionDTO(entityClass, result))
                .toList();
    }

    public <T> List<AuditEntityRevisionDTO<T>> findEntitiesModifiedInDateRange(
            Class<T> entityClass,
            LocalDateTime fromDateTime,
            LocalDateTime toDateTime
    ) {
        long fromTimestamp = toTimestamp(fromDateTime);
        long toTimestamp = toTimestamp(toDateTime);

        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.revisionProperty("timestamp").ge(fromTimestamp))
                .add(AuditEntity.revisionProperty("timestamp").le(toTimestamp))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        return results.stream()
                .map(result -> toAuditEntityRevisionDTO(entityClass, result))
                .toList();
    }

    public <T> List<AuditEntityRevisionDTO<T>> findDeletedEntities(Class<T> entityClass) {
        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.revisionType().eq(RevisionType.DEL))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getResultList();

        return results.stream()
                .map(result -> toAuditEntityRevisionDTO(entityClass, result))
                .toList();
    }

    public <T> List<Number> findRevisionsByPropertyValue(
            Class<T> entityClass,
            String propertyName,
            Object value
    ) {
        AuditReader auditReader = getAuditReader();

        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.property(propertyName).eq(value))
                .addProjection(AuditEntity.revisionNumber())
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return results.stream()
                .map(result -> (Number) result[0])
                .toList();
    }

    public <T> Number getLastRevisionWithPropertyValue(
            Class<T> entityClass,
            String propertyName,
            Object value
    ) {
        List<Number> revisions = findRevisionsByPropertyValue(entityClass, propertyName, value);

        if (revisions.isEmpty()) {
            return null;
        }

        return revisions.getLast();
    }


    private AuditReader getAuditReader() {
        return AuditReaderFactory.get(entityManager);
    }

    private <T> AuditEntityRevisionDTO<T> toAuditEntityRevisionDTO(Class<T> entityClass, Object[] result) {
        T entity = entityClass.cast(result[0]);
        ExtendedRevisionEntity revisionEntity = (ExtendedRevisionEntity) result[1];
        RevisionType revisionType = (RevisionType) result[2];

        return new AuditEntityRevisionDTO<>(
                entity,
                revisionEntity.getId(),
                revisionType,
                revisionEntity.getRevisionDateTime(),
                revisionEntity.getUsername(),
                revisionEntity.getUserId(),
                revisionEntity.getRequestMethod(),
                revisionEntity.getRequestUri(),
                revisionEntity.getTimestamp(),
                revisionEntity.getCorrelationId()
        );
    }

    private AuditRevisionDTO toAuditRevisionDTO(ExtendedRevisionEntity revisionEntity) {
        Hibernate.initialize(revisionEntity.getModifiedEntities());

        List<AuditModifiedEntityDTO> modifiedEntities = revisionEntity.getModifiedEntities()
                .stream()
                .map(this::toAuditModifiedEntityDTO)
                .toList();

        return new AuditRevisionDTO(
                revisionEntity.getId(),
                revisionEntity.getTimestamp(),
                revisionEntity.getRevisionDateTime(),
                revisionEntity.getUsername(),
                revisionEntity.getUserId(),
                revisionEntity.getIpAddress(),
                revisionEntity.getUserAgent(),
                revisionEntity.getRequestMethod(),
                revisionEntity.getRequestUri(),
                revisionEntity.getCorrelationId(),
                modifiedEntities
        );
    }

    private AuditModifiedEntityDTO toAuditModifiedEntityDTO(AuditModifiedEntity entity) {
        return new AuditModifiedEntityDTO(
                entity.getId(),
                entity.getEntityId(),
                entity.getEntityClassName(),
                entity.getEntityName()
        );
    }

    private <T> List<AuditDifferenceDTO> compareObjects(Class<T> entityClass, T oldEntity, T newEntity) {
        List<AuditDifferenceDTO> differences = new ArrayList<>();

        for (Field field : getComparableFields(entityClass)) {
            try {
                field.setAccessible(true);

                Object oldValue = field.get(oldEntity);
                Object newValue = field.get(newEntity);

                if (!Objects.equals(oldValue, newValue)) {
                    differences.add(new AuditDifferenceDTO(field.getName(), oldValue, newValue));
                }
            } catch (IllegalAccessException ignored) {
                // Si un campo no se puede leer, simplemente no lo añadimos a la comparación.
            }
        }

        return differences;

    }

    private List<Field> getComparableFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = entityClass;

        while (currentClass != null && !Object.class.equals(currentClass)) {
            Arrays.stream(currentClass.getDeclaredFields())
                    .filter(this::isComparableField)
                    .forEach(fields::add);

            currentClass = currentClass.getSuperclass();
        }

        return fields;
    }

    private boolean isComparableField(Field field) {
        int modifiers = field.getModifiers();

        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
            return false;
        }

        if (field.isAnnotationPresent(Transient.class)) {
            return false;
        }

        String fieldName = field.getName();

        return !Set.of(
                "serialVersionUID",
                "createdBy",
                "createdDate",
                "lastModifiedBy",
                "lastModifiedDate",
                "deletedBy",
                "deletedDate"
        ).contains(fieldName);
    }

    private long toTimestamp(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }

        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }


}
