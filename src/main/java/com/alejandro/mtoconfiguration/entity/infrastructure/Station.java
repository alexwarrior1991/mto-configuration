package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.*;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "STATION")
@PublishMasterDataEvent(name = "station")
public class Station extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STATION_GENERATOR = "Station_gen";
    private static final String STATION_SEQUENCE = "Station_seq";

    private String name;
    private ExecutionPackage executionPackage;
    private Set<Track> tracks = new HashSet<>();
    private Set<Disconnector> disconnectors = new HashSet<>();
    private Set<SectionInsulator> sectionInsulators = new HashSet<>();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = STATION_GENERATOR)
    @SequenceGenerator(name = STATION_GENERATOR, sequenceName = STATION_SEQUENCE, allocationSize = 1)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EXECUTION_PACKAGE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public ExecutionPackage getExecutionPackage() {
        return executionPackage;
    }

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Track> getTracks() {
        return tracks;
    }

    public void addTrack(Track track) {
        if (track != null && !containsTrack(track)) {
            getTracks().add(track);
            track.setStation(this);
        }
    }

    public void removeTrack(Track track) {
        if (track != null && containsTrack(track)) {
            getTracks().remove(track);
            track.setStation(null);
        }
    }

    public boolean containsTrack(Track track) {
        return getTracks().contains(track);
    }


    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Disconnector> getDisconnectors() {
        return disconnectors == null ? Collections.emptySet() : disconnectors;
    }

    public void addDisconnector(Disconnector disconnector) {
        if (disconnector != null && !containsDisconnector(disconnector)) {
            getDisconnectors().add(disconnector);
            disconnector.setStation(this);
        }
    }

    public void removeDisconnector(Disconnector disconnector) {
        if (disconnector != null && containsDisconnector(disconnector)) {
            getDisconnectors().remove(disconnector);
            disconnector.setStation(null);
        }
    }

    public boolean containsDisconnector(Disconnector disconnector) {
        return getDisconnectors().contains(disconnector);
    }

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<SectionInsulator> getSectionInsulators() {
        return sectionInsulators == null ? Collections.emptySet() : sectionInsulators;
    }

    public void addSectionInsulator(SectionInsulator sectionInsulator) {
        if (sectionInsulator != null && !containsSectionInsulator(sectionInsulator)) {
            getSectionInsulators().add(sectionInsulator);
            sectionInsulator.setStation(this);
        }
    }

    public void removeSectionInsulator(SectionInsulator sectionInsulator) {
        if (sectionInsulator != null && containsSectionInsulator(sectionInsulator)) {
            getSectionInsulators().remove(sectionInsulator);
            sectionInsulator.setStation(null);
        }
    }

    public boolean containsSectionInsulator(SectionInsulator sectionInsulator) {
        return getSectionInsulators().contains(sectionInsulator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Uso de instanceof para soportar correctamente los proxies de Hibernate (Lazy Loading)
        if (!(o instanceof Station that)) return false;

        // 1. Si el ID existe en ambos, usamos identidad de base de datos
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // 2. Business Key: Una estación se identifica por su nombre y el paquete de ejecución al que pertenece
        return Objects.equals(getName(), that.getName()) &&
                Objects.equals(getExecutionPackage(), that.getExecutionPackage());
    }

    @Override
    public int hashCode() {
        // Consistencia con equals: si no hay ID, usamos la Business Key
        if (getId() == null) {
            return Objects.hash(getName(), getExecutionPackage());
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof Station other)) {
            return super.compareTo(o);
        }

        // Ordenación funcional: primero por el orden natural del paquete de ejecución y luego por el nombre de la estación
        return Comparator.comparing(Station::getExecutionPackage, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Station::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
