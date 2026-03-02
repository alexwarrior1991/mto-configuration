package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(name = "ANCHORAGE_FOUNDATION_TYPE", indexes = {@Index(columnList = "code, description")})
public class AnchorageFoundationType extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ANCHORAGE_FOUNDATION_TYPE_GENERATOR = "AnchorageFoundationType_gen";
    private static final String ANCHORAGE_FOUNDATION_TYPE_SEQUENCE = "AnchorageFoundationType_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = ANCHORAGE_FOUNDATION_TYPE_GENERATOR)
    @SequenceGenerator(name = ANCHORAGE_FOUNDATION_TYPE_GENERATOR, sequenceName = ANCHORAGE_FOUNDATION_TYPE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
