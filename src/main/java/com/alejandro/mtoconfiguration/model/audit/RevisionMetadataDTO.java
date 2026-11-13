package com.alejandro.mtoconfiguration.model.audit;

import java.time.Instant;

/**
 * Quién hizo un cambio, cuándo y por qué camino: la parte {@code revision} de cada elemento de
 * {@code GET /{recurso}/{id}/revisions}.
 *
 * <p>Las seis primeras claves son las de {@code RevisionMetadataResponse} de {@code mto-stock} y
 * {@code mto-maintenance}, con el mismo significado, para que el diálogo de historial de las dos
 * aplicaciones web lea las tres APIs igual. Las tres últimas son de este servicio, que las guarda en
 * {@code audit_revision}. La IP y el agente de usuario, que también guarda, no salen: no dicen qué
 * cambió y son datos de la persona.
 *
 * @param revision      número de revisión, creciente y compartido por todo lo que cambió en la misma
 *                      transacción
 * @param revisionAt    instante del cambio
 * @param operation     qué le pasó a la fila
 * @param author        usuario del token que escribió; {@code system} si no había ninguno
 * @param source        {@code HTTP} si la revisión guardó una petición; {@code SYSTEM} si no (un
 *                      trabajo en segundo plano, una importación)
 * @param correlationId cabecera {@code X-Correlation-Id} de la petición, si la traía
 * @param userId        {@code sub} del token
 * @param requestMethod verbo de la petición que escribió
 * @param requestUri    ruta de la petición que escribió
 */
public record RevisionMetadataDTO(
        long revision,
        Instant revisionAt,
        RevisionOperation operation,
        String author,
        String source,
        String correlationId,
        String userId,
        String requestMethod,
        String requestUri
) {

    public static final String SOURCE_HTTP = "HTTP";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    /** Los metadatos de una revisión que ha leído {@code AuditService}. */
    public static RevisionMetadataDTO of(AuditEntityRevisionDTO<?> revision) {
        return new RevisionMetadataDTO(
                revision.revision().longValue(),
                Instant.ofEpochMilli(revision.timestamp()),
                RevisionOperation.of(revision.revisionType(), revision.entity()),
                revision.username(),
                revision.requestMethod() == null ? SOURCE_SYSTEM : SOURCE_HTTP,
                revision.correlationId(),
                revision.userId(),
                revision.requestMethod(),
                revision.requestUri()
        );
    }
}
