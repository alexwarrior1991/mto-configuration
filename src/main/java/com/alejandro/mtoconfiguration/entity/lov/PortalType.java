package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Entity
@Audited
@Table(indexes = { @Index(columnList = "code, description") })
public class PortalType extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PORTAL_TYPE_GENERATOR = "PortalType_gen";
    private static final String PORTAL_TYPE_SEQUENCE = "PortalType_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = PORTAL_TYPE_GENERATOR)
    @SequenceGenerator(name = PORTAL_TYPE_GENERATOR, sequenceName = PORTAL_TYPE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
