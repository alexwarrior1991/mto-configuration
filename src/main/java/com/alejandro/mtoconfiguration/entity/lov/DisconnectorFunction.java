package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(name = "DISCONNECTOR_FUNCTION", indexes = {@Index(columnList = "code, description")})
public class DisconnectorFunction extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DISCONNECTOR_FUNCTION_GENERATOR = "DisconnectorFunction_gen";
    private static final String DISCONNECTOR_FUNCTION_SEQUENCE = "DisconnectorFunction_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = DISCONNECTOR_FUNCTION_GENERATOR)
    @SequenceGenerator(name = DISCONNECTOR_FUNCTION_GENERATOR, sequenceName = DISCONNECTOR_FUNCTION_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

}
