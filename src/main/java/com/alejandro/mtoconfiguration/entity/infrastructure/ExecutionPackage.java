package com.alejandro.mtoconfiguration.entity.infrastructure;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.envers.Audited;
import java.io.Serial;
import java.time.LocalDate;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Entity
@Audited
@Table(name = "EXECUTION_PACKAGE")
public class ExecutionPackage extends CRUDEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String EXECUTION_PACKAGE_GENERATOR = "ExecutionPackage_gen";
    private static final String EXECUTION_PACKAGE_SEQUENCE = "ExecutionPackage_seq";

    private String name;
    private Boolean initialPackage;
    private Boolean enabled = true;
    private Long length;
    private LocalDate startDate;
    private LocalDate endDate;
    private BusinessEntity company;


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = EXECUTION_PACKAGE_GENERATOR)
    @SequenceGenerator(name = EXECUTION_PACKAGE_GENERATOR, sequenceName = EXECUTION_PACKAGE_SEQUENCE, allocationSize = 1)
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
    @Column(name = "INITIAL_PACKAGE", nullable = false)
    public Boolean getInitialPackage() {
        return initialPackage;
    }

    @NotNull
    @Column(name = "STATUS", nullable = false)
    public Boolean getEnabled() {
        return enabled;
    }

    @Column(name = "LENGTH", nullable = false, columnDefinition = "bigint")
    public Long getLength() {
        return length;
    }

    @Column(name = "START_DATE", nullable = false)
    public LocalDate getStartDate() {
        return startDate;
    }

    @Column(name = "END_DATE", nullable = false)
    public LocalDate getEndDate() {
        return endDate;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COMPANY_ID")
    @Audited(targetAuditMode = NOT_AUDITED)
    public BusinessEntity getCompany() {
        return company;
    }
}
