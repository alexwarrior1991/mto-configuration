package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

/**
 * Configuracion de montaje de la catenaria en un apoyo: {@code C.F.21} (catenaria flexible),
 * {@code C.C.2} (catenaria de mensula).
 *
 * <p>La trae el sinoptico de la Linha Rubi (EP RUBI); los workbooks ferroviarios no la
 * escriben. Catalogo desde {@code V21}, con la misma forma que {@link SupportType}.
 */
@Setter
@Entity
@Audited
@Table(indexes = {@Index(columnList = "code, description")})
public class AssemblyConfiguration extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ASSEMBLY_CONFIGURATION_GENERATOR = "AssemblyConfiguration_gen";
    private static final String ASSEMBLY_CONFIGURATION_SEQUENCE = "AssemblyConfiguration_seq";

    private Long drawingNumber;


    @Column(name = "DRAWING_NUMBER")
    public Long getDrawingNumber() {
        return drawingNumber;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = ASSEMBLY_CONFIGURATION_GENERATOR)
    @SequenceGenerator(name = ASSEMBLY_CONFIGURATION_GENERATOR, sequenceName = ASSEMBLY_CONFIGURATION_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
