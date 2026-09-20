package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "SECTION_INSULATOR")
@PublishMasterDataEvent(name = "section-insulator")
public class SectionInsulator extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SECTION_INSULATOR_GENERATOR = "SectionInsulator_gen";
    private static final String SECTION_INSULATOR_SEQUENCE = "SectionInsulator_seq";


    private String name;
    private Station station;
    private Boolean enabled = true;
    private BigDecimal kp;
    private SectionInsulatorInstallationType installationType;
    private Track track;
    private Track connectedTrack;
    private List<SectionInsulatorSwitch> switches = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = SECTION_INSULATOR_GENERATOR)
    @SequenceGenerator(name = SECTION_INSULATOR_GENERATOR, sequenceName = SECTION_INSULATOR_SEQUENCE, allocationSize = 1)
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
    @Column(name = "STATUS", nullable = false)
    public Boolean getEnabled() {
        return enabled;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STATION_ID", nullable = true) // nullable = true permite que sea opcional
    @Audited(targetAuditMode = NOT_AUDITED)
    public Station getStation() {
        return station;
    }

    /**
     * Punto kilométrico del aislador, en metros, con la misma precisión que {@code Profile.kp}.
     *
     * <p>El plano lo escribe {@code 110+176}: 110 km y 176 m, aquí {@code 110176.000}.
     *
     * <p>Anulable, y no sólo por compatibilidad con lo que ya hay en base: un aislador sobre una
     * conexión entre vías está realmente en los KP de sus agujas, y el suyo propio puede no estar
     * rotulado.
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
     * Si el aislador separa dos vías que conectan por una aguja ({@code TRACK_CONNECTION}) o si
     * está en medio de una sola vía ({@code IN_TRACK}).
     *
     * <p>{@code EnumType.STRING} y no {@code ORDINAL}: la columna se lee sola en una consulta y
     * añadir un valor no depende del orden de declaración.
     *
     * <p>Anulable a propósito: los aisladores que ya están en base no lo traen, y exigirlo aquí
     * convertiría el despliegue de esta migración en una migración de datos.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "INSTALLATION_TYPE", length = 30, nullable = true)
    public SectionInsulatorInstallationType getInstallationType() {
        return installationType;
    }

    /** Vía principal del aislador. En un {@code IN_TRACK} es la única. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRACK_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getTrack() {
        return track;
    }

    /** Vía con la que conecta. Nula en un {@code IN_TRACK}, que no conecta con ninguna. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONNECTED_TRACK_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Track getConnectedTrack() {
        return connectedTrack;
    }

    /**
     * Agujas por las que el aislador conecta con la vía, en orden físico.
     *
     * <p>{@code @OrderBy} y no {@code @OrderColumn}, por lo mismo que en
     * {@code Profile.getCantilevers()}: una columna de orden la mantiene la LISTA del padre, así
     * que una aguja creada suelta la dejaría a null y rompería la siguiente lectura.
     *
     * <p>Aquí sí ordena el KP, a diferencia de {@code Track.getProfiles()}: dentro de UN aislador
     * las dos o tres agujas están a metros unas de otras y no hay tramos concatenados con la
     * kilometración reiniciada que ordenar por KP pudiera mezclar. El id desempata, y PostgreSQL
     * deja los nulos al final con {@code asc}, que es donde deben ir.
     */
    @OneToMany(mappedBy = "sectionInsulator", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("deleted = false") // ver CRUDEntity: la restriccion de clase no filtra colecciones
    @OrderBy("kp ASC, id ASC")
    @Audited(targetAuditMode = NOT_AUDITED)
    public List<SectionInsulatorSwitch> getSwitches() {
        return switches;
    }

    public void addSwitch(SectionInsulatorSwitch sectionInsulatorSwitch) {
        if (sectionInsulatorSwitch != null && !containsSwitch(sectionInsulatorSwitch)) {
            getSwitches().add(sectionInsulatorSwitch);
            sectionInsulatorSwitch.setSectionInsulator(this);
        }
    }

    public void removeSwitch(SectionInsulatorSwitch sectionInsulatorSwitch) {
        if (sectionInsulatorSwitch != null && containsSwitch(sectionInsulatorSwitch)) {
            getSwitches().remove(sectionInsulatorSwitch);
            sectionInsulatorSwitch.setSectionInsulator(null);
        }
    }

    public boolean containsSwitch(SectionInsulatorSwitch sectionInsulatorSwitch) {
        return getSwitches().contains(sectionInsulatorSwitch);
    }

    /**
     * SectionInsulator heredaba el equals de BaseEntity, que compara SOLO el id. Con dos
     * instancias aun sin persistir los dos ids son null y BaseEntity las daba por
     * IGUALES, asi que Station.addSectionInsulator descartaba la segunda (y el propio HashSet de Station.sectionInsulators la habria
     * descartado igualmente). Los mappers
     * generados por MapStruct anaden los hijos uno a uno con ese adder, de modo que un
     * alta anidada con dos aisladores nuevos persistia uno solo, sin error.
     * <p>
     * A diferencia de las demas entidades de infrastructure, aqui no se usa una
     * business key: nombre + estacion identificaria el aislador, pero
     * volveria a atar el hashCode a un campo mutable. Mientras falte el id la unica respuesta correcta es que
     * dos instancias distintas son distintas, que es justo lo que hace falta para no
     * perder hijos.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        // instanceof, no getClass(), para soportar los proxies de Hibernate.
        if (!(o instanceof SectionInsulator that)) {
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
        return SectionInsulator.class.hashCode();
    }
}
