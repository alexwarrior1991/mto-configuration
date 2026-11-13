package com.alejandro.mtoconfiguration.model.audit;

/**
 * Un elemento de {@code GET /{recurso}/{id}/revisions}: los metadatos de la revisión y la fila tal
 * como quedó en ella, con la forma de su fila en las listas.
 *
 * <p>Es la vista para la API de {@link AuditEntityRevisionDTO}, que es lo que lee
 * {@code AuditService} y lleva la entidad JPA: aquí va su DTO, y los metadatos agrupados bajo
 * {@code revision}, que es la forma de {@code EntityRevisionResponse} en {@code mto-stock} y
 * {@code mto-maintenance}.
 *
 * @param revision quién, cuándo y qué operación
 * @param entity   la fila en esa revisión; {@code null} si no se puede reconstruir
 */
public record EntityRevisionDTO<D>(RevisionMetadataDTO revision, D entity) {
}
