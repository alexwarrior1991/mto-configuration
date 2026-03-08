package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "SECTION_INSULATOR")
public class SectionInsulator extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SECTION_INSULATOR_GENERATOR = "SectionInsulator_gen";
    private static final String SECTION_INSULATOR_SEQUENCE = "SectionInsulator_seq";


    private String name;
    private Station station;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = SECTION_INSULATOR_GENERATOR)
    @SequenceGenerator(name = SECTION_INSULATOR_GENERATOR, sequenceName = SECTION_INSULATOR_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Column(name = "NAME", length = 200, nullable = false)
    public String getName() {
        return name;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STATION_ID", nullable = true) // nullable = true permite que sea opcional
    @Audited(targetAuditMode = NOT_AUDITED)
    public Station getStation() {
        return station;
    }
}
