package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.masterdata.messaging.PublishMasterDataEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;

import java.io.Serial;
import java.util.*;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "TRACK")
@PublishMasterDataEvent(name = "track")
public class Track extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TRACK_GENERATOR = "Track_gen";
    private static final String TRACK_SEQUENCE = "Track_seq";

    private String name;
    private Boolean enabled = true;
    private ExecutionPackage executionPackage;
    private Set<Station> stations = new HashSet<>();
    private List<Profile> profiles = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = TRACK_GENERATOR)
    @SequenceGenerator(name = TRACK_GENERATOR, sequenceName = TRACK_SEQUENCE, allocationSize = 1)
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
    @JoinColumn(name = "EXECUTION_PACKAGE_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public ExecutionPackage getExecutionPackage() {
        return executionPackage;
    }

    /**
     * Estaciones que atraviesa la via. Varias, no una.
     *
     * <p>'TRACK 1' de EP4 es una via larga: un tramo cae dentro de ZIC, otro dentro de BIN y
     * otro dentro de HAD, y sigue siendo UNA via. Con la clave ajena unica solo cabia una de
     * las tres. La coleccion vacia tambien es una respuesta valida y frecuente: un tramo
     * entre estaciones cuelga directamente del paquete de ejecucion.
     *
     * <p>Lo que este modelo no guarda es DONDE empieza cada estacion dentro de la via. El
     * origen no marca ese limite de forma fiable —31 de las 176 vias traen el KP no monotono,
     * y una llega a un KP de 1.110.546 por un dedazo—, asi que declararlo seria inventarse una
     * precision que el dato no tiene.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "TRACK_STATION",
            joinColumns = @JoinColumn(name = "TRACK_ID"),
            inverseJoinColumns = @JoinColumn(name = "STATION_ID"))
    @Audited(targetAuditMode = NOT_AUDITED)
    public Set<Station> getStations() {
        return stations;
    }

    public void addStation(Station station) {
        if (station != null) {
            getStations().add(station);
        }
    }

    public void removeStation(Station station) {
        if (station != null) {
            getStations().remove(station);
        }
    }

    /**
     * Perfiles de la via, ordenados por su punto kilometrico.
     *
     * <p>{@code @OrderBy} y no {@code @OrderColumn}: el orden que importa aqui es el fisico a lo
     * largo de la via, no el orden en que se dieron de alta.
     *
     * <p>Desde V18 lo manda {@code orderInTrack} y el KP queda de desempate. El KP solo no
     * vale: una via puede llevar dos tramos concatenados con la kilometracion reiniciada, y
     * entonces ordenar por KP no pone el segundo detras del primero, los mezcla. Los nulos de
     * orderInTrack —un perfil dado de alta por la API— caen al final, que es lo que hace
     * PostgreSQL con 'asc' y es donde deben ir. Ademas es el orden que
     * ya usa todo lo demas ({@code findByTrackIdOrderByKpAscIdAsc}, la paginacion por keyset, la
     * exportacion), asi que antes convivian dos ordenes distintos para los mismos datos.
     *
     * <p>Y sobre todo: {@code @OrderBy} se resuelve con un ORDER BY en la consulta, sin columna
     * que mantener. La que habia, {@code insertion_order}, la rellenaba la LISTA del padre, no la
     * clave ajena del hijo, de modo que un perfil creado suelto —{@code POST /profiles} con un
     * {@code trackId}, que es lo que hace el mapeo: {@code profile.setTrack(...)} sin tocar
     * {@code track.getProfiles()}— entraba con la columna a null y la siguiente lectura de la via
     * moria con "Illegal null value for list index". Un 500 al abrir la via, provocado por una
     * peticion anterior que no habia fallado.
     */
    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("deleted = false") // ver CRUDEntity: la restriccion de clase no filtra colecciones
    @OrderBy("orderInTrack ASC, kp ASC, id ASC")
    @Audited(targetAuditMode = NOT_AUDITED)
    public List<Profile> getProfiles() {
        return profiles;
    }

    public void addProfile(Profile profile) {
        if (profile != null && !containsProfile(profile)) {
            getProfiles().add(profile);
            profile.setTrack(this);
        }
    }

    public void removeProfile(Profile profile) {
        if (profile != null && containsProfile(profile)) {
            getProfiles().remove(profile);
            profile.setTrack(null);
        }
    }

    public boolean containsProfile(Profile profile) {
        return getProfiles().contains(profile);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Uso de instanceof para manejar correctamente los proxies de Hibernate
        if (!(o instanceof Track that)) return false;

        // 1. Si ambos tienen ID, comparamos por identidad de base de datos
        if (this.getId() != null && that.getId() != null) {
            return Objects.equals(this.getId(), that.getId());
        }

        // 2. Si no hay ID (entidad nueva), usamos la Business Key: nombre + executionPackage
        // Usamos los getters para asegurar que Hibernate cargue las relaciones si son Proxies
        return Objects.equals(getName(), that.getName()) &&
                Objects.equals(getExecutionPackage(), that.getExecutionPackage());
    }

    @Override
    public int hashCode() {
        // Consistencia con equals: si el ID es nulo, usamos la Business Key
        if (getId() == null) {
            return Objects.hash(getName(), getExecutionPackage());
        }
        return Objects.hash(getId());
    }

    @Override
    public int compareTo(BaseEntity o) {
        if (!(o instanceof Track other)) {
            return super.compareTo(o);
        }

        // Ordenación funcional: primero por el nombre del paquete de ejecución y luego por el nombre de la vía
        return Comparator.comparing(Track::getExecutionPackage, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Track::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(this, other);
    }
}
