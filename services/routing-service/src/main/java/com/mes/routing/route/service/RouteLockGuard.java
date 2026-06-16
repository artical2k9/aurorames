package com.mes.routing.route.service;

import com.mes.routing.route.domain.Route;
import com.mes.routing.service.RoutingConflictException;
import com.mes.udf.api.JwtClaimsExtractor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Enforces the route edit-lock (FR-031/032). Resolves the calling user from the security context
 * (like the audit {@code AuditorAware}) so service methods need no extra parameter, and gates every
 * content mutation on the caller holding the lock. {@link #acquireFor(Route)} is used to auto-lock a
 * route to its creator/reviser; {@link #release(Route)} clears the lock on unlock/approve.
 */
@Component
public class RouteLockGuard {

    /** Current caller's KC subject (null-safe), or {@code "system"} for non-JWT contexts. */
    public String currentSubject() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return JwtClaimsExtractor.nullSafeSubject(jwtAuth.getToken());
        }
        return "system";
    }

    /** Throws 409 unless the route is locked by the calling user. Call before any content edit. */
    public void requireHeld(Route route) {
        String subject = currentSubject();
        if (route.getLockHolder() == null) {
            throw new RoutingConflictException(
                    "Route is not locked — acquire the edit lock before editing");
        }
        if (!route.getLockHolder().equals(subject)) {
            throw new RoutingConflictException(
                    "Route is locked by another user (" + route.getLockHolder() + "); you cannot edit it");
        }
    }

    /** Acquire the lock for the calling user (used on create and when starting a revision). */
    public void acquireFor(Route route) {
        route.setLockHolder(currentSubject());
        route.setLockedAt(Instant.now());
    }

    public void release(Route route) {
        route.setLockHolder(null);
        route.setLockedAt(null);
    }
}
