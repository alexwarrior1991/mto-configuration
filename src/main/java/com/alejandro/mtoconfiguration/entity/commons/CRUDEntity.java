package com.alejandro.mtoconfiguration.entity.commons;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Setter;
import org.hibernate.annotations.*;

import org.hibernate.envers.Audited;

@Setter
@DynamicUpdate
@DynamicInsert
@MappedSuperclass
@Audited
@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.ACTIVE)
public abstract class CRUDEntity extends BaseEntity {

    @Column(name = "deleted", nullable = false)
    private boolean deleted = Boolean.FALSE;

    public boolean isDeleted() {
        return deleted;
    }

    public void delete() {
        this.deleted = Boolean.TRUE;
    }

    public void restore() {
        this.deleted = false;
    }
}
