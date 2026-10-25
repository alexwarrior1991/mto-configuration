package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.enums.infrastructure.DisconnectorDriveType;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
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

    /**
     * El orden de los seccionadores de un mismo poste (V29): por nombre, sin mayusculas, y por id
     * para desempatar. Lo usan la API del perfil, el esquema de la via y el evento del perfil.
     */
    public static final Comparator<Disconnector> BY_NAME = Comparator
            .comparing(Disconnector::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(disconnector -> disconnector.getId(), Comparator.nullsLast(Comparator.<Long>naturalOrder()));

    private static final String DISCONNECTOR_GENERATOR = "Disconnector_gen";
    private static final String DISCONNECTOR_SEQUENCE = "Disconnector_seq";

    private String name;
    private Boolean onLoad;
    private Boolean normallyOpen;
    private DisconnectorDriveType driveType;
    private Station station;
    private Profile profile;
    private BigDecimal kp;
    private Track track;
    private Track connectedTrack;
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

    /**
     * Estado normal de explotación: {@code true} si está normalmente abierto, {@code false} si está
     * normalmente cerrado.
     *
     * <p>Anulable, como {@code driveType}: los seccionadores que ya estaban en base no lo traen, y
     * exigirlo convertiría el despliegue en una migración de datos que nadie puede rellenar todavía
     * (ver {@code V25}).
     */
    @Column(name = "NORMALLY_OPEN", nullable = true)
    public Boolean getNormallyOpen() {
        return normallyOpen;
    }

    /** Cómo se acciona: con motor o a mano. */
    @Enumerated(EnumType.STRING)
    @Column(name = "DRIVE_TYPE", length = 30, nullable = true)
    public DisconnectorDriveType getDriveType() {
        return driveType;
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

    /**
     * Poste del que cuelga. Opcional: no todos los seccionadores están en un poste de la línea, y
     * uno que no lo está guarda solo su estación. Un poste puede llevar varios (V29).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Profile getProfile() {
        return profile;
    }

    /**
     * KP en metros, <b>solo</b> de un seccionador sin poste: el de uno en un poste es el de su
     * perfil, y guardarlo dos veces dejaría dos datos que pueden contradecirse (V26). Lo exige
     * {@code DisconnectorValidator}.
     */
    @PositiveOrZero
    @Digits(integer = KP_INTEGER_DIGITS, fraction = KP_FRACTION_DIGITS)
    @Column(name = "KILOMETRIC_POINT",
            precision = KP_INTEGER_DIGITS + KP_FRACTION_DIGITS,
            scale = KP_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getKp() {
        return kp;
    }

    /**
     * Vía del seccionador. Sin poste, la suya (V26). Con poste, la que maniobra cuando <b>no</b> es la
     * del poste: en un haz de vías (talleres, cocheras) cuelga a veces del poste de una vía y aísla
     * otra, o une dos que no son la del poste. {@code null} con poste es «la de su poste», que no se
     * guarda dos veces: la deja a {@code null} {@code DisconnectorMapper}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRACK_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getTrack() {
        return track;
    }

    /**
     * La otra vía de un seccionador que pone dos en paralelo (V27): la suya es {@link #getTrack()}
     * si la tiene, y si no la de su poste, y esta es con la que la une. Tiene que ser otra: lo exigen
     * {@code DisconnectorValidator} y {@code DisconnectorMapper}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONNECTED_TRACK_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getConnectedTrack() {
        return connectedTrack;
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
