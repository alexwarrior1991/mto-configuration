package com.alejandro.mtoconfiguration.core.audit.envers;

import com.querydsl.core.annotations.QueryExclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(
        name = "audit_revision",
        indexes = {
                @Index(name = "idx_audit_revision_username", columnList = "username"),
                @Index(name = "idx_audit_revision_timestamp", columnList = "timestamp"),
                @Index(name = "idx_audit_revision_correlation_id", columnList = "correlation_id")
        }
)
@RevisionEntity(RevisionEntityListener.class)
@QueryExclude
public class ExtendedRevisionEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @RevisionNumber
    @Column(name = "id")
    private int id;

    @RevisionTimestamp
    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "user_id", length = 100)
    private String userId;


    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_method", length = 20)
    private String requestMethod;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @OneToMany(mappedBy = "revision", cascade = {CascadeType.PERSIST}, orphanRemoval = false)
    private Set<AuditModifiedEntity> modifiedEntities = new HashSet<>();

    public void addAuditModifiedEntity(String entityClassName, Long entityId) {
        if (entityClassName == null || entityClassName.isBlank()) {
            return;
        }
        modifiedEntities.add(new AuditModifiedEntity(this, entityClassName, entityId));
    }

    @Transient
    public LocalDateTime getRevisionDateTime() {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

}
