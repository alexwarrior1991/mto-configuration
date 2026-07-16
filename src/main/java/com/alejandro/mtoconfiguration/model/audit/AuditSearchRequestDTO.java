package com.alejandro.mtoconfiguration.model.audit;

import org.hibernate.envers.RevisionType;

import java.time.LocalDateTime;

/*

{
  "entityClassName": "com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever",
  "entityId": 10,
  "username": "alejandro",
  "revisionType": "MOD",
  "fromDateTime": "2026-07-01T00:00:00",
  "toDateTime": "2026-07-16T23:59:59"
}
* */
public record AuditSearchRequestDTO(
        String entityClassName,
        Long entityId,
        String username,
        RevisionType revisionType,
        LocalDateTime fromDateTime,
        LocalDateTime toDateTime
) {
}
