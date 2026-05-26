package com.mes.workorder.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.context.SecurityContextHolder;

public class WorkOrderAuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        WorkOrderRevisionEntity rev = (WorkOrderRevisionEntity) revisionEntity;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        rev.setActor(auth != null ? auth.getName() : "system");
    }
}
