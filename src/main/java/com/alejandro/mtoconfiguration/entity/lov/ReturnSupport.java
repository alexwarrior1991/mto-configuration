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
public class ReturnSupport extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String RETURN_SUPPORT_GENERATOR = "ReturnSupport_gen";
    private static final String RETURN_SUPPORT_SEQUENCE = "ReturnSupport_seq";

    @Column(name = "DRAWING_NUMBER")
    private Long drawingNumber;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = RETURN_SUPPORT_GENERATOR)
    @SequenceGenerator(name = RETURN_SUPPORT_GENERATOR, sequenceName = RETURN_SUPPORT_SEQUENCE, allocationSize = 1)
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
