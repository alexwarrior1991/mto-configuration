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
public class Foundation extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String FOUNDATION_GENERATOR = "Foundation_gen";
    private static final String FOUNDATION_SEQUENCE = "Foundation_seq";

    private Long drawingNumber;
    private FoundationType foundationType;


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = FOUNDATION_GENERATOR)
    @SequenceGenerator(name = FOUNDATION_GENERATOR, sequenceName = FOUNDATION_SEQUENCE, allocationSize = 1)
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
    @JoinColumn(name = "FOUNDATION_TYPE_ID")
    public FoundationType getFoundationType() {
        return foundationType;
    }

}
