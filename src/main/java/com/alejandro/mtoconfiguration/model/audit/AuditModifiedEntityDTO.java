package com.alejandro.mtoconfiguration.model.audit;

/*
* Este DTO representa una fila de audit_modified_entity.
*
* {
  "id": 1,
  "entityId": 10,
  "entityClassName": "com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever",
  "entityName": "Cantilever"
}
* */
public record AuditModifiedEntityDTO(
        Integer id,
        Long entityId,
        String entityClassName,
        String entityName
) {
}
