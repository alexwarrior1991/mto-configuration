package com.alejandro.mtoconfiguration.core.audit.envers;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@RevisionEntity(RevisionEntityListener.class)
public class ExtendedRevisionEntity extends DefaultRevisionEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "revision", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private Set<AuditModifiedEntity> modifiedEntities = new HashSet<>();

    public void addAuditModifiedEntity(String entityClassName, Long entityId) {
        modifiedEntities.add(new AuditModifiedEntity(this, entityClassName, entityId));
    }

}
