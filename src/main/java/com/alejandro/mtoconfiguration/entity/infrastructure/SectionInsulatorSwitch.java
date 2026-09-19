package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SWITCH_CODE_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TURNOUT_DENOMINATOR_MAX;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

/**
 * Aguja por la que un aislador de sección conecta con una vía.
 *
 * <p>En el plano de seccionamiento cada conexión del aislador con la vía se identifica por una
 * aguja etiquetada {@code W} y un número, en un punto kilométrico concreto y con la tangente de su
 * desvío escrita al lado: {@code W31 1:9}, {@code W35 1:12}, {@code W57 1:8}. Lo normal son dos
 * —el aislador separa las catenarias de las dos vías que ahí conectan— pero el plano también trae
 * aisladores en medio de una vía, con una sola aguja o con ninguna, y puntos donde coinciden más de
 * dos ({@code W47,W61} en el mismo KP). Por eso es una colección y no dos huecos fijos.
 *
 * <p>No lleva {@code @PublishMasterDataEvent}: no tiene servicio ni controlador propios y viaja
 * <b>dentro</b> del payload de {@code section-insulator}, así que no añade un nombre de entidad al
 * canal de datos maestros.
 */
@Setter
@Entity
@Audited
@Table(name = "SECTION_INSULATOR_SWITCH")
public class SectionInsulatorSwitch extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SECTION_INSULATOR_SWITCH_GENERATOR = "SectionInsulatorSwitch_gen";
    private static final String SECTION_INSULATOR_SWITCH_SEQUENCE = "SectionInsulatorSwitch_seq";

    private String code;
    private BigDecimal kp;
    private Integer turnoutDenominator;
    private Track track;
    private SectionInsulator sectionInsulator;
    private Boolean enabled = true;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = SECTION_INSULATOR_SWITCH_GENERATOR)
    @SequenceGenerator(name = SECTION_INSULATOR_SWITCH_GENERATOR,
            sequenceName = SECTION_INSULATOR_SWITCH_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    /** Identificador de la aguja en el plano: {@code W31}, {@code W110}. */
    @NotNull
    @Column(name = "CODE", length = SWITCH_CODE_MAX_LENGTH, nullable = false)
    public String getCode() {
        return code;
    }

    /**
     * Punto kilométrico de la aguja, en metros y con la misma precisión que {@code Profile.kp}.
     *
     * <p>El plano lo escribe {@code 110+176}, que son 110 km y 176 m: aquí, {@code 110176.000}.
     *
     * <p>Anulable porque el KP de la aguja no siempre está rotulado: el plano lo anota una vez en la
     * cabecera para un grupo de agujas y no lo repite en cada una.
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
     * El {@code 9} de {@code 1:9}: la tangente del desvío, con el numerador implícito.
     *
     * <p>Ver {@code InfrastructureConstraints.TURNOUT_DENOMINATOR_MIN} para por qué se guarda el
     * entero y no el literal del plano.
     */
    @Positive
    @Max(TURNOUT_DENOMINATOR_MAX)
    @Column(name = "TURNOUT_DENOMINATOR", nullable = true)
    public Integer getTurnoutDenominator() {
        return turnoutDenominator;
    }

    /** Vía a la que llega esta conexión del aislador. Anulable: no siempre se conoce. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRACK_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getTrack() {
        return track;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SECTION_INSULATOR_ID", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    public SectionInsulator getSectionInsulator() {
        return sectionInsulator;
    }

    @NotNull
    @Column(name = "STATUS", nullable = false)
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * El mismo equals que {@code SectionInsulator}, y por el mismo motivo: sin id no hay igualdad.
     *
     * <p>Un alta anidada manda varias agujas nuevas a la vez, todas con el id a null.
     * {@code BaseEntity} las compara sólo por id y las daría por IGUALES, de modo que el
     * {@code contains} de {@code SectionInsulator.addSwitch} —y el de
     * {@code BaseMapper.mergeCollection}, que es quien las añade una a una— descartaría todas menos
     * la primera <b>sin error</b>: se persistiría una aguja de las tres que mandó el cliente.
     *
     * <p>Tampoco vale una clave de negocio: {@code code} + aislador identifica la aguja, pero
     * volvería a atar el hashCode a un campo mutable. Mientras falte el id, la única respuesta
     * correcta es que dos instancias distintas son distintas.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        // instanceof, no getClass(), para soportar los proxies de Hibernate.
        if (!(o instanceof SectionInsulatorSwitch that)) {
            return false;
        }

        return getId() != null && that.getId() != null && Objects.equals(getId(), that.getId());
    }

    /**
     * Constante a propósito, igual que en {@code SectionInsulator}: un hashCode que dependa del id
     * cambiaría al persistir, y un hijo metido en una colección con hash antes del flush quedaría en
     * el cubo equivocado y dejaría de encontrarse.
     */
    @Override
    public int hashCode() {
        return SectionInsulatorSwitch.class.hashCode();
    }

    /** Orden físico a lo largo de la vía; el código desempata cuando no hay KP. */
    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof SectionInsulatorSwitch other)) {
            return super.compareTo(o);
        }

        return Comparator
                .comparing(SectionInsulatorSwitch::getKp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SectionInsulatorSwitch::getCode, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
