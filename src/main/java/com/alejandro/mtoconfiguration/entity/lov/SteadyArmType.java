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
public class SteadyArmType extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STEADY_ARM_TYPE_GENERATOR = "SteadyArmType_gen";
    private static final String STEADY_ARM_TYPE_SEQUENCE = "SteadyArmType_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = STEADY_ARM_TYPE_GENERATOR)
    @SequenceGenerator(name = STEADY_ARM_TYPE_GENERATOR, sequenceName = STEADY_ARM_TYPE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
