package com.mes.quality.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.context.SecurityContextHolder;

public class QualityAuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        QualityRevisionEntity rev = (QualityRevisionEntity) revisionEntity;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth != null ? auth.getName() : null;
        rev.setActor(name != null ? name : "system");
    }
}
