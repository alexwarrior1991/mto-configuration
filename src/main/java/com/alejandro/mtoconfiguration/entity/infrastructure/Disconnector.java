package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.Comparator;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "DISCONNECTOR")
@PublishMasterDataEvent(name = "disconnector")
public class Disconnector extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DISCONNECTOR_GENERATOR = "Disconnector_gen";
    private static final String DISCONNECTOR_SEQUENCE = "Disconnector_seq";

    private String name;
    private Boolean onLoad;
    private Station station;
    private Profile profile;
    private DisconnectorFunction disconnectorFunction;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = DISCONNECTOR_GENERATOR)
    @SequenceGenerator(name = DISCONNECTOR_GENERATOR, sequenceName = DISCONNECTOR_SEQUENCE, allocationSize = 1)
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
    @Column(name = "ONLOAD", nullable = false)
    public Boolean getOnLoad() {
        return onLoad;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STATION_ID", nullable = true) // nullable = true permite que sea opcional
    @Audited(targetAuditMode = NOT_AUDITED)
    public Station getStation() {
        return station;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DISCONNECTOR_FUNCTION_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public DisconnectorFunction getDisconnectorFunction() {
        return disconnectorFunction;
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Profile getProfile() {
        return profile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Uso de instanceof para soportar correctamente los proxies de Hibernate (Lazy Loading)
        if (!(o instanceof Disconnector that)) return false;

        // 1. Identidad por base de datos si el ID existe en ambos
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // 2. Business Key: Nombre + Estación (y opcionalmente Perfil)
        // Usamos los getters para asegurar la inicialización de Proxies
        return Objects.equals(getName(), that.getName()) &&
                Objects.equals(getStation(), that.getStation()) &&
                Objects.equals(getProfile(), that.getProfile());
    }

    @Override
    public int hashCode() {
        // Consistencia con equals: si no hay ID, usamos la Business Key
        if (getId() == null) {
            return Objects.hash(getName(), getStation(), getProfile());
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof Disconnector other)) {
            return super.compareTo(o);
        }

        // Ordenación funcional:
        // 1. Por el orden natural de la Estación
        // 2. Por el nombre del seccionador
        return Comparator.comparing(Disconnector::getStation, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Disconnector::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
