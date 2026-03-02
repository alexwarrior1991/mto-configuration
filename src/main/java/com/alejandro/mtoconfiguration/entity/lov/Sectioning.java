package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(name = "SECTIONING", indexes = {@Index(columnList = "code, description")})
public class Sectioning extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SECTIONING_GENERATOR = "Sectioning_gen";
    private static final String SECTIONING_SEQUENCE = "Sectioning_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = SECTIONING_GENERATOR)
    @SequenceGenerator(name = SECTIONING_GENERATOR, sequenceName = SECTIONING_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
