package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(name = "ANCHORAGE_FOUNDATION", indexes = {@Index(columnList = "code, description")})
public class AnchorageFoundation extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ANCHORAGE_FOUNDATION_GENERATOR = "AnchorageFoundation_gen";
    private static final String ANCHORAGE_FOUNDATION_SEQUENCE = "AnchorageFoundation_seq";

    private Long drawingNumber;
    private AnchorageFoundationType anchorageFoundationType;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = ANCHORAGE_FOUNDATION_GENERATOR)
    @SequenceGenerator(name = ANCHORAGE_FOUNDATION_GENERATOR, sequenceName = ANCHORAGE_FOUNDATION_SEQUENCE, allocationSize = 1)
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
    @JoinColumn(name = "ANCHORAGE_FOUNDATION_TYPE_ID")
    public AnchorageFoundationType getAnchorageFoundationType() {
        return anchorageFoundationType;
    }
}
