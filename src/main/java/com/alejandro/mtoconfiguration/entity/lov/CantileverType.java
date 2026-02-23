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
public class CantileverType extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String CANTILEVER_TYPE_GENERATOR = "CantileverType_gen";
    private static final String CANTILEVER_TYPE_SEQUENCE = "CantileverType_seq";

    @Column(name = "DRAWING_NUMBER")
    private Long drawingNumber;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = CANTILEVER_TYPE_GENERATOR)
    @SequenceGenerator(name = CANTILEVER_TYPE_GENERATOR, sequenceName = CANTILEVER_TYPE_SEQUENCE, allocationSize = 1)
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
}
