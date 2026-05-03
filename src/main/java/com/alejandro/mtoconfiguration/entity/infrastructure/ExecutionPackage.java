package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "EXECUTION_PACKAGE")
public class ExecutionPackage extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String EXECUTION_PACKAGE_GENERATOR = "ExecutionPackage_gen";
    private static final String EXECUTION_PACKAGE_SEQUENCE = "ExecutionPackage_seq";

    private String name;
    private Boolean initialPackage;
    private Long length;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean enabled = true;
    private BusinessEntity company;
    private Set<Track> tracks = new HashSet<>();
    private Set<Station> stations = new HashSet<>();


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = EXECUTION_PACKAGE_GENERATOR)
    @SequenceGenerator(name = EXECUTION_PACKAGE_GENERATOR, sequenceName = EXECUTION_PACKAGE_SEQUENCE, allocationSize = 1)
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
    @Column(name = "INITIAL_PACKAGE", nullable = false)
    public Boolean getInitialPackage() {
        return initialPackage;
    }

    @Column(name = "LENGTH", nullable = false, columnDefinition = "bigint")
    public Long getLength() {
        return length;
    }

    @Column(name = "START_DATE", nullable = false)
    public LocalDate getStartDate() {
        return startDate;
    }

    @Column(name = "END_DATE", nullable = false)
    public LocalDate getEndDate() {
        return endDate;
    }

    @Column(name = "ENABLED", nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COMPANY_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public BusinessEntity getCompany() {
        return company;
    }

    @OneToMany(mappedBy = "executionPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Track> getTracks() {
        return tracks;
    }

    public void addTrack(Track track) {
        if (track != null && !containsTrack(track)) {
            getTracks().add(track);
            track.setExecutionPackage(this);
        }
    }

    public void removeTrack(Track track) {
        if (track != null && containsTrack(track)) {
            getTracks().remove(track);
            track.setExecutionPackage(null);
        }
    }

    public boolean containsTrack(Track track) {
        return getTracks().contains(track);
    }

    @OneToMany(mappedBy = "executionPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Station> getStations() {
        return stations;
    }

    public void addStation(Station station) {
        if (station != null && !containsStation(station)) {
            getStations().add(station);
            station.setExecutionPackage(this);
        }
    }

    public void removeStation(Station station) {
        if (station != null && containsStation(station)) {
            getStations().remove(station);
            station.setExecutionPackage(null);
        }
    }

    public boolean containsStation(Station station) {
        return getStations().contains(station);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExecutionPackage that)) return false;

        // Si el ID existe, lo usamos como identificador único (Business Key recomendada)
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // Si no hay ID (entidad nueva), comparamos por campos únicos de negocio
        return Objects.equals(getName(), that.getName()) &&
                Objects.equals(getStartDate(), that.getStartDate()) &&
                Objects.equals(getCompany(), that.getCompany());
    }

    @Override
    public int hashCode() {
        // Si el ID es nulo, usamos campos de negocio.
        // Si no es nulo, lo ideal es que el hashcode sea consistente.
        if (getId() == null) {
            return Objects.hash(name, startDate, company);
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof ExecutionPackage other)) {
            return super.compareTo(o);
        }

        return Comparator.comparing(ExecutionPackage::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ExecutionPackage::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
