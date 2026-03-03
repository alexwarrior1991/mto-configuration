package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Setter
@Entity
@Audited
@Table(name = "DISCONNECTOR")
public class Disconnector extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DISCONNECTOR_GENERATOR = "Disconnector_gen";
    private static final String DISCONNECTOR_SEQUENCE = "Disconnector_seq";

    private String name;
    private Boolean onLoad;
    private Station station;
    private Profile profile;
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

    @Column(name = "NAME", length = 200, nullable = false)
    public String getName() {
        return name;
    }

    @NotNull
    @Column(name = "ONLOAD", nullable = false)
    public Boolean getOnLoad() {
        return onLoad;
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = true)
    @Audited(targetAuditMode = NOT_AUDITED)
    public Profile getProfile() {
        return profile;
    }
}
