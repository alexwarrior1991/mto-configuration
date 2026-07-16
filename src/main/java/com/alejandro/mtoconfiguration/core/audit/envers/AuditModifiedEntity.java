package com.alejandro.mtoconfiguration.core.audit.envers;

import com.querydsl.core.annotations.QueryExclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Setter
@Getter
@Entity
@Table(
        name = "audit_modified_entity",
        indexes = {
                @Index(name = "idx_audit_modified_entity_revision", columnList = "revision_id"),
                @Index(name = "idx_audit_modified_entity_class_id", columnList = "entity_class_name, entity_id")
        }
)
@QueryExclude
public class AuditModifiedEntity {

    @Id
    @GeneratedValue
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private ExtendedRevisionEntity revision;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_class_name", nullable = false, length = 300)
    private String entityClassName;

    @Column(name = "entity_name", length = 100)
    private String entityName;

    public AuditModifiedEntity() {
    }

    public AuditModifiedEntity(ExtendedRevisionEntity extendedRevisionEntity, String entityClassName, Long entityId) {
        this.revision = extendedRevisionEntity;
        this.entityClassName = entityClassName;
        this.entityName = extractSimpleName(entityClassName);
        this.entityId = entityId;
    }

    private String extractSimpleName(String entityClassName) {
        if (entityClassName == null) {
            return null;
        }
        int lastDot = entityClassName.lastIndexOf('.');
        return lastDot >= 0 ? entityClassName.substring(lastDot + 1) : entityClassName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditModifiedEntity that)) {
            return false;
        }
        return Objects.equals(entityId, that.entityId)
                && Objects.equals(entityClassName, that.entityClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityClassName, entityId);
    }

}
