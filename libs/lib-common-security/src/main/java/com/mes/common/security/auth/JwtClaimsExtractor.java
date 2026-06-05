package com.mes.common.security.auth;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;
import java.util.List;

public class JwtClaimsExtractor {

    private final Jwt jwt;

    public JwtClaimsExtractor(Jwt jwt) {
        this.jwt = jwt;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles() {
        Object roles = jwt.getClaim("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return Collections.emptyList();
    }

    public String getOrgId() {
        String orgId = jwt.getClaimAsString("org_id");
        if (orgId == null) {
            throw new MissingClaimException("org_id");
        }
        return orgId;
    }

    public String getSub() {
        return jwt.getSubject();
    }

    /** Returns sub if present; falls back to preferred_username; falls back to "unknown". */
    public String nullSafeSubject() {
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        String username = jwt.getClaimAsString("preferred_username");
        return (username != null && !username.isBlank()) ? username : "unknown";
    }
}
