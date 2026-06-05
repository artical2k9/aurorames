package com.mes.audit.envers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MesRevisionListenerTest {

    private final MesRevisionListener listener = new MesRevisionListener();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        ServiceIdentityContext.clear();
    }

    @Test
    void populatesUserIdFromJwtSubjectClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user:abc-123")
            .claim("realm_access", java.util.Map.of("roles", List.of("AUDIT_READ")))
            .build();
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        MesRevisionEntity entity = new MesRevisionEntity();
        listener.newRevision(entity);

        assertThat(entity.getUserId()).isEqualTo("user:abc-123");
    }

    @Test
    void fallsBackToSystemPrincipalWhenNoJwtPresent() {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", null));
        ServiceIdentityContext.set("test-service");

        MesRevisionEntity entity = new MesRevisionEntity();
        listener.newRevision(entity);

        assertThat(entity.getUserId()).isEqualTo("system:test-service");
    }

    @Test
    void fallsBackToSystemUnknownWhenNoAuthAndNoServiceContext() {
        SecurityContextHolder.clearContext();
        ServiceIdentityContext.clear();

        MesRevisionEntity entity = new MesRevisionEntity();
        listener.newRevision(entity);

        assertThat(entity.getUserId()).startsWith("system:");
    }

    @Test
    void nullSubClaim_fallsBackToPreferredUsername() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("preferred_username", "admin@test.org")
            .build();
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        MesRevisionEntity entity = new MesRevisionEntity();
        listener.newRevision(entity);

        assertThat(entity.getUserId()).isEqualTo("admin@test.org");
    }

    @Test
    void nullSubAndNoUsername_fallsBackToSystemUnknown() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("org_id", "00000000-0000-0000-0000-000000000001")
            .build();
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        ServiceIdentityContext.clear();

        MesRevisionEntity entity = new MesRevisionEntity();
        listener.newRevision(entity);

        assertThat(entity.getUserId()).isEqualTo("system:unknown");
    }

    @Test
    void setsServiceSourceOnRevisionEntity() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user:xyz")
            .build();
        SecurityContextHolder.getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        MesRevisionEntity entity = new MesRevisionEntity();
        listener.newRevision(entity);

        assertThat(entity.getServiceSource()).isNotBlank();
    }
}
