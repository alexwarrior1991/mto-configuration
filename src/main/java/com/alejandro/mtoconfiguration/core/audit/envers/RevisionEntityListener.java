package com.alejandro.mtoconfiguration.core.audit.envers;

import org.hibernate.envers.EntityTrackingRevisionListener;
import org.hibernate.envers.RevisionType;

public class RevisionEntityListener implements EntityTrackingRevisionListener {

    @Override
    public void entityChanged(Class entityClass, String entityName, Object entityId, RevisionType revisionType, Object revisionEntity) {
        final ExtendedRevisionEntity r = (ExtendedRevisionEntity) revisionEntity;
        r.addAuditModifiedEntity(entityName, (Long) entityId);
    }

    @Override
    public void newRevision(Object revisionEntity) {

    }
}
