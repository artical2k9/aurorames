package com.mes.inventory.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.context.SecurityContextHolder;

public class InventoryAuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        InventoryRevisionEntity rev = (InventoryRevisionEntity) revisionEntity;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth != null ? auth.getName() : null;
        rev.setActor(name != null ? name : "system");
    }
}
