package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundation;
import com.alejandro.mtoconfiguration.entity.lov.Foundation;
import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "PROFILE")
public class Profile extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PROFILE_GENERATOR = "Profile_gen";
    private static final String PROFILE_SEQUENCE = "Profile_seq";

    private Track track;
    private Disconnector disconnector;
    private List<Cantilever> cantilevers = new ArrayList<>();
    private ProfileStatus profileStatus;
    private Foundation foundation;
    private AnchorageFoundation anchorageFoundation;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = PROFILE_GENERATOR)
    @SequenceGenerator(name = PROFILE_GENERATOR, sequenceName = PROFILE_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRACK_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getTrack() {
        return track;
    }

    @Size(max = 3, message = "The profile must have between 0 and 3 cantilevers")
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "insertion_order") // Crea una columna física para guardar el índice [0, 1, 2...]
    @Audited(targetAuditMode = NOT_AUDITED)
    public List<Cantilever> getCantilevers() {
        return cantilevers;
    }

    public void addCantilever(Cantilever cantilever) {
        if (cantilever != null && !containsCantilever(cantilever)) {
            getCantilevers().add(cantilever);
            cantilever.setProfile(this);
        }
    }

    public void removeCantilever(Cantilever cantilever) {
        if (cantilever != null && containsCantilever(cantilever)) {
            getCantilevers().remove(cantilever);
            cantilever.setProfile(null);
        }
    }

    public boolean containsCantilever(Cantilever cantilever) {
        return getCantilevers().contains(cantilever);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_STATUS_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public ProfileStatus getProfileStatus() {
        return profileStatus;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOUNDATION_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Foundation getFoundation() {
        return foundation;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ANCHORAGE_FOUNDATION_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public AnchorageFoundation getAnchorageFoundation() {
        return anchorageFoundation;
    }

    @OneToOne(
            mappedBy = "profile",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY,
            orphanRemoval = false
    )
    public Disconnector getDisconnector() {
        return disconnector;
    }

    @PreRemove
    private void preRemove() {
        if (disconnector != null) {
            disconnector.setProfile(null);
        }
    }

    public void addDisconnector(Disconnector disconnector) {
        if (disconnector != null) {
            this.setDisconnector(disconnector);
            disconnector.setProfile(this);
        }
    }

    public void removeDisconnector() {
        if (this.disconnector != null) {
            this.disconnector.setProfile(null);
            this.setDisconnector(null);
        }
    }

    public boolean containsDisconnector() {
        return this.disconnector != null;
    }
}
