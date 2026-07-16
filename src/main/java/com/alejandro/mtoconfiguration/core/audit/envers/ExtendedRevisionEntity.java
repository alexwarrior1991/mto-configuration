package com.alejandro.mtoconfiguration.core.audit.envers;

import com.querydsl.core.annotations.QueryExclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@RevisionEntity(RevisionEntityListener.class)
@QueryExclude
public class ExtendedRevisionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @RevisionNumber
    private int id;

    @RevisionTimestamp
    private long timestamp;

    @OneToMany(mappedBy = "revision", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private Set<AuditModifiedEntity> modifiedEntities = new HashSet<>();

    public void addAuditModifiedEntity(String entityClassName, Long entityId) {
        modifiedEntities.add(new AuditModifiedEntity(this, entityClassName, entityId));
    }

}
