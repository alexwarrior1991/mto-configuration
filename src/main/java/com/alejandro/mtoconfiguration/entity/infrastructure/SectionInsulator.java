package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "SECTION_INSULATOR")
@PublishMasterDataEvent(name = "section-insulator")
public class SectionInsulator extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SECTION_INSULATOR_GENERATOR = "SectionInsulator_gen";
    private static final String SECTION_INSULATOR_SEQUENCE = "SectionInsulator_seq";


    private String name;
    private Station station;
    private Boolean enabled = true;

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

    @Column(name = "NAME", length = NAME_MAX_LENGTH, nullable = false)
    public String getName() {
        return name;
    }

    @NotNull
    @Column(name = "STATUS", nullable = false)
    public Boolean getEnabled() {
        return enabled;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STATION_ID", nullable = true) // nullable = true permite que sea opcional
    @Audited(targetAuditMode = NOT_AUDITED)
    public Station getStation() {
        return station;
    }

    /**
     * SectionInsulator heredaba el equals de BaseEntity, que compara SOLO el id. Con dos
     * instancias aun sin persistir los dos ids son null y BaseEntity las daba por
     * IGUALES, asi que Station.addSectionInsulator descartaba la segunda (y el propio HashSet de Station.sectionInsulators la habria
     * descartado igualmente). Los mappers
     * generados por MapStruct anaden los hijos uno a uno con ese adder, de modo que un
     * alta anidada con dos aisladores nuevos persistia uno solo, sin error.
     * <p>
     * A diferencia de las demas entidades de infrastructure, aqui no se usa una
     * business key: nombre + estacion identificaria el aislador, pero
     * volveria a atar el hashCode a un campo mutable. Mientras falte el id la unica respuesta correcta es que
     * dos instancias distintas son distintas, que es justo lo que hace falta para no
     * perder hijos.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        // instanceof, no getClass(), para soportar los proxies de Hibernate.
        if (!(o instanceof SectionInsulator that)) {
            return false;
        }

        // Sin id en alguno de los dos no hay nada que comparar: son el mismo objeto
        // (caso ya resuelto arriba) o son distintos.
        return getId() != null && that.getId() != null && Objects.equals(getId(), that.getId());
    }

    /**
     * Constante a proposito. El hashCode no puede depender del id, porque cambiaria al
     * persistir la entidad: un hijo metido en un HashSet antes del flush quedaria en el
     * cubo equivocado y dejaria de encontrarse. Constante es consistente con equals en
     * los dos estados, que es lo que exige el contrato.
     */
    @Override
    public int hashCode() {
        return SectionInsulator.class.hashCode();
    }
}
