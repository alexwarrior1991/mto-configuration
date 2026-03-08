package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    private String profileId;
    private BigDecimal kp;

    private Track track;
    private Disconnector disconnector;
    private List<Cantilever> cantilevers = new ArrayList<>();

    private Anchorage anchorage;
    private AnchorageFoundation anchorageFoundation;
    private Foundation foundation;
    private PoleType poleType;
    private Portal portal;
    private ProfileStatus profileStatus;
    private ReturnSupport returnSupport;
    private Sectioning sectioning;

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

    @NotNull
    @Column(name = "PROFILE_ID", length = 50, nullable = false)
    public String getProfileId() {
        return profileId;
    }

    @NotNull
    @PositiveOrZero
    @Digits(integer = 9, fraction = 3)
    @Column(name = "KILOMETRIC_POINT", precision = 12, scale = 3, nullable = false)
    public BigDecimal getKp() {
        return kp;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ANCHORAGE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Anchorage getAnchorage() {
        return anchorage;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ANCHORAGE_FOUNDATION_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public AnchorageFoundation getAnchorageFoundation() {
        return anchorageFoundation;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOUNDATION_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Foundation getFoundation() {
        return foundation;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "POLE_TYPE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public PoleType getPoleType() {
        return poleType;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PORTAL_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Portal getPortal() {
        return portal;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_STATUS_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public ProfileStatus getProfileStatus() {
        return profileStatus;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RETURN_SUPPORT_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public ReturnSupport getReturnSupport() {
        return returnSupport;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SECTIONING_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Sectioning getSectioning() {
        return sectioning;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Uso de instanceof para soportar proxies de Hibernate
        if (!(o instanceof Profile that)) return false;

        // 1. Identidad por base de datos si el ID existe
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // 2. Business Key: El identificador del perfil y la vía a la que pertenece
        return Objects.equals(getProfileId(), that.getProfileId()) &&
                Objects.equals(getTrack(), that.getTrack());
    }

    @Override
    public int hashCode() {
        // Consistencia con equals: ID o Business Key
        if (getId() == null) {
            return Objects.hash(getProfileId(), getTrack());
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof Profile other)) {
            return super.compareTo(o);
        }

        // Ordenación funcional:
        // 1. Por el orden natural de la vía (Track)
        // 2. Por el punto kilométrico (KP) para asegurar orden geográfico/lineal
        // 3. Por el profileId como desempate final
        return Comparator.comparing(Profile::getTrack, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Profile::getKp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Profile::getProfileId, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
