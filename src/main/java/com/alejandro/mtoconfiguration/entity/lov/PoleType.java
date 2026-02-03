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
public class PoleType extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String POLETYPE_GENERATOR = "PoleType_gen";
    private static final String POLETYPE_SEQUENCE = "PoleType_seq";

    private Long drawingNumber;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = POLETYPE_GENERATOR)
    @SequenceGenerator(name = POLETYPE_GENERATOR, sequenceName = POLETYPE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
