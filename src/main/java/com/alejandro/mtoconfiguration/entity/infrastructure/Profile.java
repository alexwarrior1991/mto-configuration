package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.*;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.HEIGHT_CANTILEVER_SUPPORT_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.POLE_GAUGE_LOCATION_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.POLE_GAUGE_LOCATION_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.PROFILE_ID_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.PROFILE_MAX_CANTILEVERS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.RAIL_POLE_DISTANCE_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.RAIL_POLE_DISTANCE_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SPAN_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SPAN_INTEGER_DIGITS;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@PublishMasterDataEvent(name = "profile")
@Table(name = "PROFILE", indexes = {
        @Index(name = "IDX_PROFILE_TRACK_KP_ID", columnList = "TRACK_ID, KILOMETRIC_POINT, id")
})
@NamedEntityGraph(
        name = "Profile.export",
        attributeNodes = {
                @NamedAttributeNode("track"),
                @NamedAttributeNode("foundation"),
                @NamedAttributeNode("poleType"),
                @NamedAttributeNode("profileStatus"),
                @NamedAttributeNode(value = "cantilevers", subgraph = "cantilevers-subgraph")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "cantilevers-subgraph",
                        attributeNodes = {
                                @NamedAttributeNode("cantileverType"),
                                @NamedAttributeNode(value = "steadyArm", subgraph = "steadyarm-subgraph")
                        }
                ),
                @NamedSubgraph(
                        name = "steadyarm-subgraph",
                        attributeNodes = {
                                @NamedAttributeNode("steadyArmType")
                        }
                )
        }
)
public class Profile extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PROFILE_GENERATOR = "Profile_gen";
    private static final String PROFILE_SEQUENCE = "Profile_seq";

    private String profileId;
    private BigDecimal kp;

    private BigDecimal span;
    private BigDecimal heightCantileverSupport;
    private BigDecimal poleGaugeLocation;
    private BigDecimal railPoleDistance;

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
    private Set<Sectioning> sectionings = new LinkedHashSet<>();
    private DisconnectorFunction sectioningFeeding;

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
    @Column(name = "PROFILE_ID", length = PROFILE_ID_MAX_LENGTH, nullable = false)
    public String getProfileId() {
        return profileId;
    }

    @NotNull
    @PositiveOrZero
    @Digits(integer = KP_INTEGER_DIGITS, fraction = KP_FRACTION_DIGITS)
    @Column(name = "KILOMETRIC_POINT",
            precision = KP_INTEGER_DIGITS + KP_FRACTION_DIGITS,
            scale = KP_FRACTION_DIGITS,
            nullable = false)
    public BigDecimal getKp() {
        return kp;
    }

    /**
     * Vano hasta el perfil siguiente, en metros.
     *
     * <p>El sentido lo fija el origen: en los workbooks el valor no está en la fila del perfil
     * sino en la intermedia, entre ese perfil y el siguiente, así que pertenece al tramo que
     * arranca aquí y no al punto.
     *
     * <p>Opcional: el origen lo trae en el 95 % de los perfiles, no en todos.
     */
    @PositiveOrZero
    @Digits(integer = SPAN_INTEGER_DIGITS, fraction = SPAN_FRACTION_DIGITS)
    @Column(name = "SPAN",
            precision = SPAN_INTEGER_DIGITS + SPAN_FRACTION_DIGITS,
            scale = SPAN_FRACTION_DIGITS)
    public BigDecimal getSpan() {
        return span;
    }

    /** Altura del soporte de ménsula, en milímetros. Opcional. */
    @PositiveOrZero
    @Digits(integer = HEIGHT_CANTILEVER_SUPPORT_INTEGER_DIGITS,
            fraction = HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS)
    @Column(name = "HEIGHT_CANTILEVER_SUPPORT",
            precision = HEIGHT_CANTILEVER_SUPPORT_INTEGER_DIGITS + HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS,
            scale = HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS)
    public BigDecimal getHeightCantileverSupport() {
        return heightCantileverSupport;
    }

    /** Separación del poste respecto al gálibo, en milímetros. Opcional. */
    @PositiveOrZero
    @Digits(integer = POLE_GAUGE_LOCATION_INTEGER_DIGITS, fraction = POLE_GAUGE_LOCATION_FRACTION_DIGITS)
    @Column(name = "POLE_GAUGE_LOCATION",
            precision = POLE_GAUGE_LOCATION_INTEGER_DIGITS + POLE_GAUGE_LOCATION_FRACTION_DIGITS,
            scale = POLE_GAUGE_LOCATION_FRACTION_DIGITS)
    public BigDecimal getPoleGaugeLocation() {
        return poleGaugeLocation;
    }

    /**
     * Distancia entre el carril y el poste, en milímetros.
     *
     * <p><b>Lleva signo a propósito</b>, y por eso es la única de las tres sin
     * {@code @PositiveOrZero}: indica a qué lado de la vía queda el poste. En el origen va de
     * -6.290 a 9.125.
     */
    @Digits(integer = RAIL_POLE_DISTANCE_INTEGER_DIGITS, fraction = RAIL_POLE_DISTANCE_FRACTION_DIGITS)
    @Column(name = "RAIL_POLE_DISTANCE",
            precision = RAIL_POLE_DISTANCE_INTEGER_DIGITS + RAIL_POLE_DISTANCE_FRACTION_DIGITS,
            scale = RAIL_POLE_DISTANCE_FRACTION_DIGITS)
    public BigDecimal getRailPoleDistance() {
        return railPoleDistance;
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

    /**
     * Seccionamientos del perfil. <b>Varios</b>, no uno.
     *
     * <p>Un perfil puede llevar mas de uno a la vez —es corriente en estaciones: 'A/S P50' son
     * dos— y el modelo tenia una clave ajena, que solo admite uno. La celda del origen con dos
     * valores no era un error de tecleo: era el dominio que no cabia en el esquema.
     *
     * <p>{@code Set} y no {@code List} porque el orden no significa nada: un perfil TIENE estos
     * seccionamientos, no los tiene en un orden. {@code LinkedHashSet} para que dos lecturas
     * devuelvan lo mismo, que es lo unico que se necesita para que exportaciones y eventos sean
     * reproducibles.
     *
     * <p>Es la unica relacion N:M del perfil: las demas listas de valores llevan una sola por
     * perfil, y ampliarlas seria complicar el modelo sin motivo.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "PROFILE_SECTIONING",
            joinColumns = @JoinColumn(name = "PROFILE_ID"),
            inverseJoinColumns = @JoinColumn(name = "SECTIONING_ID"))
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Sectioning> getSectionings() {
        return sectionings;
    }

    /**
     * Elemento de seccionamiento y alimentación del perfil (columna {@code Sectioning Feeding} de
     * los workbooks).
     *
     * <p>Reutiliza el catálogo {@link DisconnectorFunction} en lugar de una LOV propia: el bloque
     * {@code FEEDING} de la leyenda define 22 códigos —{@code Disc}, {@code Disc/NS},
     * {@code Disc/IO}, {@code Disc/SI}, {@code Disc/t}, {@code LoadB} y variantes, {@code ED},
     * {@code ED/T}, {@code SurgeA}, {@code VoltageD}, {@code SECT-I}, {@code CurrentT},
     * {@code FS-1}, {@code FS-1D}, {@code FS/PP-2}, {@code FS/PP-3}, {@code PP-2}, {@code PP-3} y
     * {@code PP-4}— y los 22 ya están en ese catálogo. El campo se llama por su papel; el
     * catálogo no se duplica.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SECTIONING_FEEDING_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public DisconnectorFunction getSectioningFeeding() {
        return sectioningFeeding;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRACK_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getTrack() {
        return track;
    }

    @Size(max = PROFILE_MAX_CANTILEVERS, message = "The profile must have between 0 and 3 cantilevers")
    /**
     * Mensulas del perfil, en orden estable por id.
     *
     * <p>El orden entre las mensulas de un perfil no significa nada, asi que basta con que sea
     * <b>estable</b> —que dos lecturas devuelvan lo mismo— y para eso sirve el id.
     *
     * <p>{@code @OrderBy} y no {@code @OrderColumn} por el mismo motivo que en
     * {@code Track.getProfiles()}: la columna de orden la mantenia la lista del padre, de manera
     * que una mensula creada suelta ({@code POST /cantilevers} con un {@code profileId}) la dejaba
     * a null y rompia la siguiente lectura del perfil.
     */
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("deleted = false") // ver CRUDEntity: la restriccion de clase no filtra colecciones
    @OrderBy("id ASC")
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


    @SQLRestriction("deleted = false") // ver CRUDEntity
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
