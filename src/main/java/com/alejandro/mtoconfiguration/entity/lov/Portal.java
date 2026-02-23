package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(indexes = {@Index(columnList = "code, description")})
public class Portal extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PORTAL_GENERATOR = "Portal_gen";
    private static final String PORTAL_SEQUENCE = "Portal_seq";

    private Long drawingNumber;
    private PortalType portalType;


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = PORTAL_GENERATOR)
    @SequenceGenerator(name = PORTAL_GENERATOR, sequenceName = PORTAL_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Column(name = "DRAWING_NUMBER")
    public Long getDrawingNumber() {
        return drawingNumber;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PORTAL_TYPE_ID")
    public PortalType getPortalType() {
        return portalType;
    }
}
