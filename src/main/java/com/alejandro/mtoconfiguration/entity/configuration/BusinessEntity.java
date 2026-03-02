package com.alejandro.mtoconfiguration.entity.configuration;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.lov.ComercialEntityType;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
public class BusinessEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String BUSINESS_ENTITY_GENERATOR = "BusinessEntity_gen";
    private static final String BUSINESS_ENTITY_SEQUENCE = "BusinessEntity_seq";

    private String identificationNumber;
    private String name;
    private String code;

    private ComercialEntityType comercialEntityType;


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = BUSINESS_ENTITY_GENERATOR)
    @SequenceGenerator(name = BUSINESS_ENTITY_GENERATOR, sequenceName = BUSINESS_ENTITY_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Column(nullable = false)
    public String getName() {
        return name;
    }

    @Column(nullable = false)
    public String getCode() {
        return code;
    }

    @Column(nullable = false, unique = true)
    public String getIdentificationNumber() {
        return identificationNumber;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COMERCIAL_ENTITY_TYPE_ID", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    public ComercialEntityType getComercialEntityType() {
        return comercialEntityType;
    }

    @Transient
    public boolean isCustoms() {
        return comercialEntityType != null && comercialEntityType.isCustoms();
    }

    @Transient
    public boolean isConsignee() {
        return comercialEntityType != null && comercialEntityType.isConsignee();
    }

    @Transient
    public boolean isRailwayCompany() {
        return comercialEntityType != null && comercialEntityType.isRailwayCompany();
    }

}
