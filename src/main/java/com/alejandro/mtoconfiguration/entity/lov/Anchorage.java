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
public class Anchorage extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ANCHORAGE_GENERATOR = "Anchorage_gen";
    private static final String ANCHORAGE_SEQUENCE = "Anchorage_seq";

    private Long drawingNumber;


    @Column(name = "DRAWING_NUMBER")
    public Long getDrawingNumber() {
        return drawingNumber;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = ANCHORAGE_GENERATOR)
    @SequenceGenerator(name = ANCHORAGE_GENERATOR, sequenceName = ANCHORAGE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
