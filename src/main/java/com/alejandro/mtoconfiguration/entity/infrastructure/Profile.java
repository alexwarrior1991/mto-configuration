package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundation;
import com.alejandro.mtoconfiguration.entity.lov.Foundation;
import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

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
}
