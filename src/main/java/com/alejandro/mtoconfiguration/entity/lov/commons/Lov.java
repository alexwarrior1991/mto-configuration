package com.alejandro.mtoconfiguration.entity.lov.commons;

import com.alejandro.mtoconfiguration.core.audit.EntityListener;
import com.alejandro.mtoconfiguration.entity.commons.BaseEntity;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serial;

@DynamicUpdate
@DynamicInsert
@Audited
@MappedSuperclass
@Setter
@EntityListeners(value = {AuditingEntityListener.class, EntityListener.class})
public abstract class Lov extends BaseEntity implements ILov {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String description;
    private boolean enabled;

    /**
     * Longitud alineada con {@code V9__widen_lov_code_and_unique.sql}. Los codigos
     * reales de los workbooks de Execution Package no caben en 10 caracteres:
     * {@code CP/TX-P/1100}, {@code 2HEB-300 V (CP)}, {@code AnMC/Tunnel}.
     *
     * <p>Si se cambia aqui hay que cambiar la migracion: {@code ddl-auto: validate}
     * no comprueba longitudes de varchar y la discrepancia no saldria al arrancar.
     */
    @NotNull(message = ErrorCodes.VALIDATION_REQUIRED_FIELD)
    @Size(max = 40)
    @Column(length = 40, nullable = false)
    @Override
    public String getCode() {
        return code;
    }

    @NotNull(message = ErrorCodes.VALIDATION_REQUIRED_FIELD)
    @Size(max = 200)
    @Column(length = 200, nullable = false)
    @Override
    public String getDescription() {
        return description;
    }

    @NotNull(message = ErrorCodes.VALIDATION_REQUIRED_FIELD)
    @Column(nullable = false)
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public static String getCode(Lov e) {
        return e != null ? e.getCode() : null;
    }

    public static String getDescription(Lov e) {
        return e != null ? e.getDescription() : null;
    }

    public static boolean isEnabled(Lov e) {
        return e != null && e.isEnabled();
    }

    public static Long getId(Lov entity) {
        return entity != null ? entity.getId() : null;
    }
}
