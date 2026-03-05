package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "STEADY_ARM")
public class SteadyArm extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STEADY_ARM_GENERATOR = "SteadyArm_gen";
    private static final String STEADY_ARM_SEQUENCE = "SteadyArm_seq";

    private Long length;
    private SteadyArmType steadyArmType;
    private Cantilever cantilever;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = STEADY_ARM_GENERATOR)
    @SequenceGenerator(name = STEADY_ARM_GENERATOR, sequenceName = STEADY_ARM_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @NotNull
    @Min(0)
    @Max(2000)
    @Column(name = "LENGTH", nullable = false)
    public Long getLength() {
        return length;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STEADY_ARM_TYPE_ID", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    public SteadyArmType getSteadyArmType() {
        return steadyArmType;
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CANTILEVER_ID", unique = true) // unique=true asegura que sea 1 a 1
    public Cantilever getCantilever() {
        return cantilever;
    }
}
