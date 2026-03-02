package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.enums.lov.ComercialEntityTypeValue;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(indexes = {@Index(columnList = "code, description")})
public class ComercialEntityType extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String COMERCIAL_ENTITY_TYPE_GENERATOR = "ComercialEntityType_gen";
    private static final String COMERCIAL_ENTITY_TYPE_SEQUENCE = "ComercialEntityType_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = COMERCIAL_ENTITY_TYPE_GENERATOR)
    @SequenceGenerator(name = COMERCIAL_ENTITY_TYPE_GENERATOR, sequenceName = COMERCIAL_ENTITY_TYPE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Transient
    public boolean isCustoms() {
        return ComercialEntityTypeValue.CUSTOMS.getCode().equalsIgnoreCase(getCode());
    }

    @Transient
    public boolean isConsignee() {
        return ComercialEntityTypeValue.CONSIGNEE.getCode().equalsIgnoreCase(getCode());
    }

    @Transient
    public boolean isRailwayCompany() {
        return ComercialEntityTypeValue.RAILWAY_COMPANY.getCode().equalsIgnoreCase(getCode());
    }
}
