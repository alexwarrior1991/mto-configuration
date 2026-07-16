package com.alejandro.mtoconfiguration.model.audit;

import java.time.LocalDateTime;
import java.util.List;
/*
* Este DTO representa la revisión completa.
* Es decir, responde a:
*
Quién hizo el cambio
Cuándo lo hizo
Desde dónde lo hizo
Qué endpoint llamó
Qué entidades tocó
* */
public record AuditRevisionDTO(
        Number revision,
        Long timestamp,
        LocalDateTime revisionDateTime,
        String username,
        String userId,
        String ipAddress,
        String userAgent,
        String requestMethod,
        String requestUri,
        String correlationId,
        List<AuditModifiedEntityDTO> modifiedEntities
) {
}
