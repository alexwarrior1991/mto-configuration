package com.alejandro.mtoconfiguration.model.audit;

import org.hibernate.envers.RevisionType;

import java.time.LocalDateTime;

/*
Este DTO representa una versión histórica de una entidad.

{
  "entity": {
    "id": 10,
    "code": "CANT-001"
  },
  "revision": 15,
  "revisionType": "MOD",
  "revisionDateTime": "2026-07-16T09:50:00",
  "username": "alejandro",
  "userId": "abc-123",
  "requestMethod": "PUT",
  "requestUri": "/api/v1/cantilever/10"
}
* */

public record AuditEntityRevisionDTO<T>(
        T entity,
        Number revision,
        RevisionType revisionType,
        LocalDateTime revisionDateTime,
        String username,
        String userId,
        String requestMethod,
        String requestUri
) {
}
