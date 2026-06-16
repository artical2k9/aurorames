package com.mes.routing.route.service;

import com.mes.routing.route.domain.Route;
import com.mes.routing.service.RoutingConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class RouteLockGuardTest {

    private final RouteLockGuard guard = new RouteLockGuard();

    private void authAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .claim("sub", subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireHeld_unlockedRoute_throwsConflict() {
        authAs("alice");
        assertThatThrownBy(() -> guard.requireHeld(new Route()))
                .isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void requireHeld_lockedByAnother_throwsConflict() {
        authAs("alice");
        Route r = new Route();
        r.setLockHolder("bob");
        assertThatThrownBy(() -> guard.requireHeld(r)).isInstanceOf(RoutingConflictException.class);
    }

    @Test
    void requireHeld_lockedBySelf_passes() {
        authAs("alice");
        Route r = new Route();
        r.setLockHolder("alice");
        assertThatCode(() -> guard.requireHeld(r)).doesNotThrowAnyException();
    }

    @Test
    void acquireFor_setsHolderToCaller_andReleaseClears() {
        authAs("alice");
        Route r = new Route();
        guard.acquireFor(r);
        assertThat(r.getLockHolder()).isEqualTo("alice");
        assertThat(r.getLockedAt()).isNotNull();
        guard.release(r);
        assertThat(r.getLockHolder()).isNull();
        assertThat(r.getLockedAt()).isNull();
    }

    @Test
    void currentSubject_noAuthentication_returnsSystem() {
        SecurityContextHolder.clearContext();
        assertThat(guard.currentSubject()).isEqualTo("system");
    }
}
