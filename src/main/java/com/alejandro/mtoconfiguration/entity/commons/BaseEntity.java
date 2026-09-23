package com.alejandro.mtoconfiguration.entity.commons;

import com.alejandro.mtoconfiguration.core.audit.EntityListener;
import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@DynamicUpdate
@DynamicInsert
@MappedSuperclass
@Audited
@EntityListeners(value = {AuditingEntityListener.class, EntityListener.class})
public abstract class BaseEntity implements IEntity, Comparable<BaseEntity>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    protected Long id;

    private LocalDateTime createDate;

    private LocalDateTime versionDate;

    private String createUser;

    private String versionUser;

    private Integer versionNumber = 1;

    private boolean versionIncremented;

    private boolean dirty;

    @NotNull(message = "validation.model.base.createDate.notNull")
    @CreatedDate
    @Column(name = "create_date", nullable = false, updatable = false)
    @Override
    public LocalDateTime getCreateDate() {
        return createDate;
    }


    @Override
    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    @NotNull(message = "validation.model.base.versionDate.notNull")
    @LastModifiedDate
    @Column(name = "version_date", nullable = false)
    @Override
    public LocalDateTime getVersionDate() {
        return versionDate;
    }

    @Override
    public void setVersionDate(LocalDateTime versionDate) {
        this.versionDate = versionDate;
    }

    @NotNull(message = "validation.model.base.createUser.notNull")
    @CreatedBy
    @Column(name = "create_user", nullable = false, updatable = false)
    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    @NotNull(message = "validation.model.base.versionUser.notNull")
    @LastModifiedBy
    @Column(name = "version_user", nullable = false)
    @Override
    public String getVersionUser() {
        return versionUser;
    }

    @Override
    public void setVersionUser(String versionUser) {
        this.versionUser = versionUser;
    }

    @NotNull(message = "validation.model.base.versionNumber.notNull")
    @Version
    @Column(name = "version_number", nullable = false)
    @Override
    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * Bloqueo optimista con el {@code versionNumber} que el cliente leyo.
     *
     * <p>Hace falta comprobarlo a mano: el servicio carga la entidad en la misma transaccion en la
     * que escribe, asi que el {@code @Version} de JPA nunca ve un conflicto, y los mappers no
     * copian la version del DTO a la entidad.</p>
     *
     * <p>{@code null} no comprueba nada: quien no manda version escribe sobre lo que haya. Es lo
     * que hacen a proposito el importador del maestro, que es la fuente del dato, y los trabajos
     * que construyen el DTO en Java. Con version, tiene que ser la de la fila; si no, alguien ha
     * guardado desde la lectura y aplicar el cambio pisaria el suyo (409 {@code CON-001}).</p>
     */
    @Override
    public void validateVersion(Integer versionNumber) throws ConcurrencyException {
        if (versionNumber != null && !versionNumber.equals(this.versionNumber)) {
            throw new ConcurrencyException("%s %d ha cambiado desde que se leyo: llego la version %d y va por la %d"
                    .formatted(Hibernate.getClass(this).getSimpleName(), getId(), versionNumber, this.versionNumber));
        }
    }

    @Override
    @Transient
    public boolean isVersionIncremented() {
        return versionIncremented;
    }

    @Override
    @Transient
    public void setVersionIncremented(boolean versionIncremented) {
        this.versionIncremented = versionIncremented;
    }

    @Override
    @Transient
    public boolean isDirty() {
        return dirty;
    }

    @Override
    @Transient
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) return false;

        return new EqualsBuilder().append(this.getClass(), o.getClass())
                .append(this.getId(), ((BaseEntity) o).getId()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).append(getId()).append(this.getClass().getName()).toHashCode();
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (o == null) {
            return -1;
        }

        if (this.getId() == null) {
            return -1;
        }

        return this.getId().compareTo(o.getId());
    }

    public static Long getId(BaseEntity entity) {
        return entity != null ? entity.getId() : null;
    }
}
