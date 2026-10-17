package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointKind;
import com.alejandro.mtoconfiguration.enums.infrastructure.TrackAccessPointSide;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.COORDINATE_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.COORDINATE_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LATITUDE_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LATITUDE_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LONGITUDE_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LONGITUDE_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TONNES_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TONNES_INTEGER_DIGITS;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

/**
 * Punto de acceso a la vía: por donde un vehículo bivial sale de la carretera y se encarrila (V28).
 *
 * <p>Es un maestro suelto, como el seccionador sin poste: cuelga de su vía, que es obligatoria, y
 * opcionalmente de una estación, pero ni la vía ni la estación lo llevan en una colección. Así no se
 * escribe anidado desde ningún otro servicio, y su evento ({@code track-access-point}) es el único
 * sitio por el que lo conocen los consumidores: {@code mto-resources} lo materializa y aplica con él
 * las reglas de la flota al registrar dónde se encarrila un vehículo.
 */
@Setter
@Entity
@Audited
@Table(name = "TRACK_ACCESS_POINT")
@PublishMasterDataEvent(name = "track-access-point")
public class TrackAccessPoint extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TRACK_ACCESS_POINT_GENERATOR = "TrackAccessPoint_gen";
    private static final String TRACK_ACCESS_POINT_SEQUENCE = "TrackAccessPoint_seq";

    private String name;
    private Track track;
    private Station station;
    private BigDecimal kp;
    private TrackAccessPointKind kind;
    private TrackAccessPointSide side;
    private Boolean roadRailAccess = true;
    private BigDecimal maxAxleLoadT;
    private BigDecimal maxGrossWeightT;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean enabled = true;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = TRACK_ACCESS_POINT_GENERATOR)
    @SequenceGenerator(name = TRACK_ACCESS_POINT_GENERATOR, sequenceName = TRACK_ACCESS_POINT_SEQUENCE,
            allocationSize = 1)
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

    /** La vía a la que da acceso. Obligatoria: un acceso que no lleva a ninguna vía no es un acceso. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TRACK_ID", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getTrack() {
        return track;
    }

    /** Estación en la que está, si está en una. Opcional: muchos accesos están en plena vía. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STATION_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Station getStation() {
        return station;
    }

    /**
     * Punto kilométrico del acceso, en metros, como el de un aislador ({@code SectionInsulator.kp}):
     * {@code 110+176} del plano es {@code 110176.000}. Opcional, también como el del aislador.
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

    /** Acceso preparado, paso a nivel o cochera. Obligatorio: es lo primero que mira la flota. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", length = 30, nullable = false)
    public TrackAccessPointKind getKind() {
        return kind;
    }

    /** Por qué lado de la vía se entra. Obligatorio. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "SIDE", length = 10, nullable = false)
    public TrackAccessPointSide getSide() {
        return side;
    }

    /** Si sirve para encarrilar biviales. Por defecto sí: es para lo que existe un punto de acceso. */
    @NotNull
    @Column(name = "ROAD_RAIL_ACCESS", nullable = false)
    public Boolean getRoadRailAccess() {
        return roadRailAccess;
    }

    /** Carga por eje máxima que admite, en toneladas. Opcional: sin dato, la flota no limita. */
    @PositiveOrZero
    @Digits(integer = TONNES_INTEGER_DIGITS, fraction = TONNES_FRACTION_DIGITS)
    @Column(name = "MAX_AXLE_LOAD_T",
            precision = TONNES_INTEGER_DIGITS + TONNES_FRACTION_DIGITS,
            scale = TONNES_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getMaxAxleLoadT() {
        return maxAxleLoadT;
    }

    /** Peso bruto máximo que admite, en toneladas. Opcional. */
    @PositiveOrZero
    @Digits(integer = TONNES_INTEGER_DIGITS, fraction = TONNES_FRACTION_DIGITS)
    @Column(name = "MAX_GROSS_WEIGHT_T",
            precision = TONNES_INTEGER_DIGITS + TONNES_FRACTION_DIGITS,
            scale = TONNES_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getMaxGrossWeightT() {
        return maxGrossWeightT;
    }

    /** Latitud en grados decimales (WGS 84). Opcional, pero va con la longitud: las dos o ninguna. */
    @DecimalMin(LATITUDE_MIN)
    @DecimalMax(LATITUDE_MAX)
    @Digits(integer = COORDINATE_INTEGER_DIGITS, fraction = COORDINATE_FRACTION_DIGITS)
    @Column(name = "LATITUDE",
            precision = COORDINATE_INTEGER_DIGITS + COORDINATE_FRACTION_DIGITS,
            scale = COORDINATE_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getLatitude() {
        return latitude;
    }

    /** Longitud en grados decimales (WGS 84). Opcional, con la latitud. */
    @DecimalMin(LONGITUDE_MIN)
    @DecimalMax(LONGITUDE_MAX)
    @Digits(integer = COORDINATE_INTEGER_DIGITS, fraction = COORDINATE_FRACTION_DIGITS)
    @Column(name = "LONGITUDE",
            precision = COORDINATE_INTEGER_DIGITS + COORDINATE_FRACTION_DIGITS,
            scale = COORDINATE_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getLongitude() {
        return longitude;
    }

    @NotNull
    @Column(name = "STATUS", nullable = false)
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Identidad por id, como {@code SectionInsulator}: {@code instanceof} para los proxies de
     * Hibernate, y dos instancias sin id son distintas salvo que sean la misma. No se usa una clave de
     * negocio (vía y nombre) porque ataría el {@code hashCode} a campos que cambian.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof TrackAccessPoint that)) {
            return false;
        }

        return getId() != null && that.getId() != null && Objects.equals(getId(), that.getId());
    }

    /** Constante, por lo mismo que en {@code SectionInsulator}: el id cambia al persistir. */
    @Override
    public int hashCode() {
        return TrackAccessPoint.class.hashCode();
    }
}
