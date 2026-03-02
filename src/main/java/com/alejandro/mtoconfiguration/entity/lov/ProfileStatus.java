package com.alejandro.mtoconfiguration.entity.lov;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.enums.infrastructure.ProfileStatusValue;
import jakarta.persistence.*;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serial;

@Setter
@Entity
@Audited
@Table(name = "PROFILE_STATUS", indexes = {@Index(columnList = "code, description")})
public class ProfileStatus extends Lov {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PROFILE_STATUS_GENERATOR = "ProfileStatus_gen";
    private static final String PROFILE_STATUS_SEQUENCE = "ProfileStatus_seq";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = PROFILE_STATUS_GENERATOR)
    @SequenceGenerator(name = PROFILE_STATUS_GENERATOR, sequenceName = PROFILE_STATUS_SEQUENCE, allocationSize = 1)
    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Transient
    public boolean isDefinitive() {
        return ProfileStatusValue.DEFINITIVE.getCode().equalsIgnoreCase(getCode());
    }

    @Transient
    public boolean isProvisional() {
        return ProfileStatusValue.PROVISIONAL.getCode().equalsIgnoreCase(getCode());
    }

    @Transient
    public boolean isDraft() {
        return ProfileStatusValue.DRAFT.getCode().equalsIgnoreCase(getCode());
    }
}
