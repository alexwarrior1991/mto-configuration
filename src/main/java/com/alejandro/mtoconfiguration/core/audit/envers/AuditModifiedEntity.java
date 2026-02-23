package com.alejandro.mtoconfiguration.core.audit.envers;

import com.querydsl.core.annotations.QueryExclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "audit_modified_entity", indexes = {@Index(columnList = "revision_id")})
@QueryExclude
public class AuditModifiedEntity {

    @Id
    @GeneratedValue
    private Integer id;

    @ManyToOne
    private ExtendedRevisionEntity revision;

    private Long entityId;
    private String entityClassName;

    public AuditModifiedEntity() {

    }

    public AuditModifiedEntity(ExtendedRevisionEntity extendedRevisionEntity, String entityClassName, Long entityId) {
        this.revision = extendedRevisionEntity;
        this.entityClassName = entityClassName;
        this.entityId = entityId;
    }

}
