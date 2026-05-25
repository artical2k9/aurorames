package com.mes.audit.envers;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class MesRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        if (!(revisionEntity instanceof MesRevisionEntity entity)) {
            return;
        }
        entity.setUserId(resolveUserId());
        entity.setServiceSource(resolveServiceSource());
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        String serviceIdentity = ServiceIdentityContext.get();
        if (serviceIdentity != null && !serviceIdentity.isBlank()) {
            return "system:" + serviceIdentity;
        }
        return "system:unknown";
    }

    private String resolveServiceSource() {
        String service = ServiceIdentityContext.get();
        if (service != null && !service.isBlank()) {
            return service;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return "jwt-authenticated";
        }
        return "unknown";
    }
}
