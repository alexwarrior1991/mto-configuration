package com.alejandro.mtoconfiguration.model.audit;

import org.hibernate.envers.RevisionType;

import java.time.LocalDateTime;

public record AuditEntityChangeDTO<T>(
        T entity,
        Number revision,
        RevisionType revisionType,
        LocalDateTime revisionDateTime,
        String username,
        String entityName,
        Long entityId
) {
}
