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
  "requestUri": "/api/v1/cantilever/10",
  "timestamp": 1784195400000,
  "correlationId": "5f0c..."
}

timestamp es el instante de la revision en milisegundos, sin zona: revisionDateTime es el mismo
instante en la zona del servidor. correlationId es la cabecera X-Correlation-Id de la peticion que
escribio, si la traia.
* */

public record AuditEntityRevisionDTO<T>(
        T entity,
        Number revision,
        RevisionType revisionType,
        LocalDateTime revisionDateTime,
        String username,
        String userId,
        String requestMethod,
        String requestUri,
        long timestamp,
        String correlationId
) {
}
