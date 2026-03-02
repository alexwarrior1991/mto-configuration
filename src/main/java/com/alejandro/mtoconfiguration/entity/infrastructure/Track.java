package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "TRACK")
public class Track extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TRACK_GENERATOR = "Track_gen";
    private static final String TRACK_SEQUENCE = "Track_seq";

    private String name;
    private Boolean enabled = true;
    private ExecutionPackage executionPackage;
    private Set<Profile> profiles = new HashSet<>();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = TRACK_GENERATOR)
    @SequenceGenerator(name = TRACK_GENERATOR, sequenceName = TRACK_SEQUENCE, allocationSize = 1)
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

    @NotNull
    @Column(name = "STATUS", nullable = false)
    public Boolean getEnabled() {
        return enabled;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EXECUTION_PACKAGE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public ExecutionPackage getExecutionPackage() {
        return executionPackage;
    }

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Profile> getProfiles() {
        return profiles;
    }

    public void addProfile(Profile profile) {
        if (profile != null && !containsProfile(profile)) {
            getProfiles().add(profile);
            profile.setTrack(this);
        }
    }

    public void removeProfile(Profile profile) {
        if (profile != null && containsProfile(profile)) {
            getProfiles().remove(profile);
            profile.setTrack(null);
        }
    }

    public boolean containsProfile(Profile profile) {
        return getProfiles().contains(profile);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Uso de instanceof para manejar correctamente los proxies de Hibernate
        if (!(o instanceof Track that)) return false;

        // 1. Si ambos tienen ID, comparamos por identidad de base de datos
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // 2. Si no hay ID (entidad nueva), usamos la Business Key: nombre + executionPackage
        // Usamos los getters para asegurar que Hibernate cargue las relaciones si son Proxies
        return Objects.equals(getName(), that.getName()) &&
                Objects.equals(getExecutionPackage(), that.getExecutionPackage());
    }

    @Override
    public int hashCode() {
        // Consistencia con equals: si el ID es nulo, usamos la Business Key
        if (getId() == null) {
            return Objects.hash(getName(), getExecutionPackage());
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof Track other)) {
            return super.compareTo(o);
        }

        // Ordenación funcional: primero por el nombre del paquete de ejecución y luego por el nombre de la vía
        return Comparator.comparing(Track::getExecutionPackage, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Track::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
