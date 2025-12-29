package com.alejandro.mtoconfiguration.entity.commons;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@DynamicUpdate
@DynamicInsert
@MappedSuperclass
@Audited
public abstract class BaseDocumentEntity extends  BaseEntity{

    @Serial
    private static final long serialVersionUID = 1L;

    private String documentNumber;
    @Getter
    private String observations;

    @Column( nullable = false, updatable = false )
    public String getDocumentNumber() {
        return documentNumber;
    }

}
