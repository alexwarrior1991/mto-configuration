package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.Objects;
import java.math.BigDecimal;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.ARM_ANGLE_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CATENARY_HEIGHT_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CATENARY_HEIGHT_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_ELEVATION_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_ELEVATION_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_HEIGHT_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_HEIGHT_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.CW_HEIGHT_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.STAGGER_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.STAGGER_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.WIND_DEFLECTION_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.WIND_DEFLECTION_INTEGER_DIGITS;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "CANTILEVER")
@PublishMasterDataEvent(name = "cantilever")
public class Cantilever extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String CANTILEVER_GENERATOR = "Cantilever_gen";
    private static final String CANTILEVER_SEQUENCE = "Cantilever_seq";


    private BigDecimal cwHeight;
    private BigDecimal stagger;
    private BigDecimal catenaryHeight;
    private BigDecimal cwElevation;
    private BigDecimal windDeflection;
    private BigDecimal armAngle;

    private CantileverType cantileverType;
    private Profile profile;
    private SteadyArm steadyArm;


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = CANTILEVER_GENERATOR)
    @SequenceGenerator(name = CANTILEVER_GENERATOR, sequenceName = CANTILEVER_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @DecimalMin(value = CW_HEIGHT_MIN, inclusive = true)
    @Digits(integer = CW_HEIGHT_INTEGER_DIGITS, fraction = CW_HEIGHT_FRACTION_DIGITS)
    @Column(name = "CW_HEIGHT",
            precision = CW_HEIGHT_INTEGER_DIGITS + CW_HEIGHT_FRACTION_DIGITS,
            scale = CW_HEIGHT_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getCwHeight() {
        return cwHeight;
    }

    @Digits(integer = STAGGER_INTEGER_DIGITS, fraction = STAGGER_FRACTION_DIGITS)
    @Column(name = "STAGGER",
            precision = STAGGER_INTEGER_DIGITS + STAGGER_FRACTION_DIGITS,
            scale = STAGGER_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getStagger() {
        return stagger;
    }

    @Digits(integer = CATENARY_HEIGHT_INTEGER_DIGITS, fraction = CATENARY_HEIGHT_FRACTION_DIGITS)
    @Column(name = "CATENARY_HEIGHT",
            precision = CATENARY_HEIGHT_INTEGER_DIGITS + CATENARY_HEIGHT_FRACTION_DIGITS,
            scale = CATENARY_HEIGHT_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getCatenaryHeight() {
        return catenaryHeight;
    }

    @Digits(integer = CW_ELEVATION_INTEGER_DIGITS, fraction = CW_ELEVATION_FRACTION_DIGITS)
    @Column(name = "CW_ELEVATION",
            precision = CW_ELEVATION_INTEGER_DIGITS + CW_ELEVATION_FRACTION_DIGITS,
            scale = CW_ELEVATION_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getCwElevation() {
        return cwElevation;
    }

    @Digits(integer = WIND_DEFLECTION_INTEGER_DIGITS, fraction = WIND_DEFLECTION_FRACTION_DIGITS)
    @Column(name = "WIND_DEFLECTION",
            precision = WIND_DEFLECTION_INTEGER_DIGITS + WIND_DEFLECTION_FRACTION_DIGITS,
            scale = WIND_DEFLECTION_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getWindDeflection() {
        return windDeflection;
    }

    @DecimalMin(value = ARM_ANGLE_MIN, inclusive = true)
    @DecimalMax(value = ARM_ANGLE_MAX, inclusive = true)
    @Digits(integer = ARM_ANGLE_INTEGER_DIGITS, fraction = ARM_ANGLE_FRACTION_DIGITS)
    @Column(name = "ARM_ANGLE",
            precision = ARM_ANGLE_INTEGER_DIGITS + ARM_ANGLE_FRACTION_DIGITS,
            scale = ARM_ANGLE_FRACTION_DIGITS,
            nullable = true)
    public BigDecimal getArmAngle() {
        return armAngle;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = false)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Profile getProfile() {
        return profile;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CANTILEVER_TYPE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public CantileverType getCantileverType() {
        return cantileverType;
    }

    @OneToOne(mappedBy = "cantilever", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    public SteadyArm getSteadyArm() {
        return steadyArm;
    }

    public void addSteadyArm(SteadyArm steadyArm) {
        if (steadyArm != null) {
            this.setSteadyArm(steadyArm);
            steadyArm.setCantilever(this);
        }
    }

    public void removeSteadyArm() {
        if (this.steadyArm != null) {
            this.steadyArm.setCantilever(null);
            this.setSteadyArm(null);
        }
    }

    public boolean containsSteadyArm(SteadyArm steadyArm) {
        return this.steadyArm != null;
    }

    /**
     * Cantilever heredaba el equals de BaseEntity, que compara SOLO el id. Con dos
     * instancias aun sin persistir los dos ids son null y BaseEntity las daba por
     * IGUALES, asi que Profile.addCantilever descartaba la segunda. Los mappers
     * generados por MapStruct anaden los hijos uno a uno con ese adder, de modo que un
     * alta anidada con dos meniscos nuevos persistia uno solo, sin error.
     * <p>
     * A diferencia de las demas entidades de infrastructure, aqui no se usa una
     * business key porque Cantilever no tiene ninguna: dentro de un perfil
     * lo unico que distingue a un menisco de otro es su posicion en la lista. Mientras falte el id la unica respuesta correcta es que
     * dos instancias distintas son distintas, que es justo lo que hace falta para no
     * perder hijos.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        // instanceof, no getClass(), para soportar los proxies de Hibernate.
        if (!(o instanceof Cantilever that)) {
            return false;
        }

        // Sin id en alguno de los dos no hay nada que comparar: son el mismo objeto
        // (caso ya resuelto arriba) o son distintos.
        return getId() != null && that.getId() != null && Objects.equals(getId(), that.getId());
    }

    /**
     * Constante a proposito. El hashCode no puede depender del id, porque cambiaria al
     * persistir la entidad: un hijo metido en un HashSet antes del flush quedaria en el
     * cubo equivocado y dejaria de encontrarse. Constante es consistente con equals en
     * los dos estados, que es lo que exige el contrato.
     */
    @Override
    public int hashCode() {
        return Cantilever.class.hashCode();
    }
}
