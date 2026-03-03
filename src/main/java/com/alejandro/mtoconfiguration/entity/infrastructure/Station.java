package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "STATION")
public class Station extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STATION_GENERATOR = "Station_gen";
    private static final String STATION_SEQUENCE = "Station_seq";

    private String name;
    private ExecutionPackage executionPackage;
    private Set<Track> tracks = new HashSet<>();
    private Set<Disconnector> disconnectors = new HashSet<>();

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

    @Column(name = "NAME", length = 200, nullable = false)
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

}
