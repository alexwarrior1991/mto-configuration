package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "CANTILEVER")
public class Cantilever extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String CANTILEVER_GENERATOR = "Cantilever_gen";
    private static final String CANTILEVER_SEQUENCE = "Cantilever_seq";


    private BigDecimal cwHeight;
    private BigDecimal stagger;
    private BigDecimal catenaryHeight;
    private BigDecimal cwElevation;
    private BigDecimal windDeflection;
    private BigDecimal armAngle;

    private CantileverType cantileverType;
    private Profile profile;


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = CANTILEVER_GENERATOR)
    @SequenceGenerator(name = CANTILEVER_GENERATOR, sequenceName = CANTILEVER_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @DecimalMin(value = "0.000", inclusive = true)
    @Digits(integer = 1, fraction = 3)
    @Column(name = "CW_HEIGHT", precision = 4, scale = 3, nullable = true)
    public BigDecimal getCwHeight() {
        return cwHeight;
    }

    @Digits(integer = 3, fraction = 0)
    @Column(name = "STAGGER", precision = 3, scale = 0, nullable = true)
    public BigDecimal getStagger() {
        return stagger;
    }

    @Digits(integer = 1, fraction = 3)
    @Column(name = "CATENARY_HEIGHT", precision = 4, scale = 3, nullable = true)
    public BigDecimal getCatenaryHeight() {
        return catenaryHeight;
    }

    @Digits(integer = 1, fraction = 3)
    @Column(name = "CW_ELEVATION", precision = 4, scale = 3, nullable = true)
    public BigDecimal getCwElevation() {
        return cwElevation;
    }

    @Digits(integer = 1, fraction = 3)
    @Column(name = "WIND_DEFLECTION", precision = 4, scale = 3, nullable = true)
    public BigDecimal getWindDeflection() {
        return windDeflection;
    }

    @DecimalMin(value = "-90.000", inclusive = true)
    @DecimalMax(value = "90.000", inclusive = true)
    @Digits(integer = 2, fraction = 3)
    @Column(name = "ARM_ANGLE", precision = 5, scale = 3, nullable = true)
    public BigDecimal getArmAngle() {
        return armAngle;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Profile getProfile() {
        return profile;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CANTILEVER_TYPE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public CantileverType getCantileverType() {
        return cantileverType;
    }
}
