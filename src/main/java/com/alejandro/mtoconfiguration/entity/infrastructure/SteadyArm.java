package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.SteadyArmType;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.Comparator;
import java.util.Objects;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "STEADY_ARM")
@PublishMasterDataEvent(name = "steady-arm")
public class SteadyArm extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STEADY_ARM_GENERATOR = "SteadyArm_gen";
    private static final String STEADY_ARM_SEQUENCE = "SteadyArm_seq";

    private Long length;
    private SteadyArmType steadyArmType;
    private Cantilever cantilever;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = STEADY_ARM_GENERATOR)
    @SequenceGenerator(name = STEADY_ARM_GENERATOR, sequenceName = STEADY_ARM_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @NotNull
    @Min(0)
    @Max(2000)
    @Column(name = "LENGTH", nullable = false)
    public Long getLength() {
        return length;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STEADY_ARM_TYPE_ID", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    public SteadyArmType getSteadyArmType() {
        return steadyArmType;
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CANTILEVER_ID", unique = true) // unique=true asegura que sea 1 a 1
    public Cantilever getCantilever() {
        return cantilever;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Uso de instanceof para soportar correctamente los proxies de Hibernate (Lazy Loading)
        if (!(o instanceof SteadyArm that)) return false;

        // 1. Identidad por base de datos si el ID existe en ambos
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // 2. Business Key: Relación con Cantilever (relación 1:1)
        // Usamos los getters para asegurar la inicialización de Proxies si es necesario
        return Objects.equals(getCantilever(), that.getCantilever());
    }

    @Override
    public int hashCode() {
        // Consistencia con equals: si no hay ID, usamos la Business Key
        if (getId() == null) {
            return Objects.hash(getCantilever());
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof SteadyArm other)) {
            return super.compareTo(o);
        }

        // Ordenación funcional:
        // 1. Por el orden natural del Cantilever (que a su vez ordena por Profile/Track)
        // 2. Por la longitud como desempate técnico
        return Comparator.comparing(SteadyArm::getCantilever, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SteadyArm::getLength, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
