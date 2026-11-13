package com.alejandro.mtoconfiguration.model.audit;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import org.hibernate.envers.RevisionType;

/**
 * Qué le pasó a la fila en una revisión de su historial, con los nombres que usan
 * {@code mto-stock} y {@code mto-maintenance} en su {@code /revisions}.
 */
public enum RevisionOperation {
    CREATED,
    UPDATED,
    DELETED;

    /**
     * La operación de una revisión de Envers.
     *
     * <p>El borrado de un maestro es lógico: Envers lo apunta como una modificación ({@code MOD}) que
     * deja la fila con {@code deleted = true}, y quien lee el historial tiene que ver ahí una baja.
     * Una fila ya borrada no se vuelve a escribir (no se puede cargar, por su
     * {@code @SQLRestriction}), así que una revisión con la fila borrada solo puede ser la que la
     * borró. El borrado de un catálogo es físico, y ese sí llega como {@code DEL}.
     */
    public static RevisionOperation of(RevisionType revisionType, Object entityAtRevision) {
        if (revisionType == RevisionType.DEL) {
            return DELETED;
        }

        if (entityAtRevision instanceof CRUDEntity crud && crud.isDeleted()) {
            return DELETED;
        }

        return revisionType == RevisionType.ADD ? CREATED : UPDATED;
    }
}
