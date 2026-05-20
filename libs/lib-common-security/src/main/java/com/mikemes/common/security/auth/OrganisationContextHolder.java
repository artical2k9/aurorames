package com.mikemes.common.security.auth;

import java.util.Optional;

public final class OrganisationContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private OrganisationContextHolder() {}

    public static void setOrgId(String orgId) {
        CONTEXT.set(orgId);
    }

    public static Optional<String> getOrgId() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
