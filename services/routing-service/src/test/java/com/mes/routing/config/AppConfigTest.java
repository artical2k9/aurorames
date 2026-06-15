package com.mes.routing.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    private final AuditorAware<String> auditor = new AppConfig().auditorProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(String given, String family, String username) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("sub");
        if (given != null) {
            b.claim("given_name", given);
        }
        if (family != null) {
            b.claim("family_name", family);
        }
        if (username != null) {
            b.claim("preferred_username", username);
        }
        return b.build();
    }

    private void authenticate(Jwt token) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of()));
    }

    @Test
    void noAuthentication_returnsSystem() {
        assertThat(auditor.getCurrentAuditor()).contains("system");
    }

    @Test
    void givenAndFamilyName_returnsFullName() {
        authenticate(jwt("Ada", "Lovelace", "ada"));
        assertThat(auditor.getCurrentAuditor()).contains("Ada Lovelace");
    }

    @Test
    void givenOnly_returnsGiven() {
        authenticate(jwt("Ada", null, "ada"));
        assertThat(auditor.getCurrentAuditor()).contains("Ada");
    }

    @Test
    void familyOnly_returnsFamily() {
        authenticate(jwt(null, "Lovelace", "ada"));
        assertThat(auditor.getCurrentAuditor()).contains("Lovelace");
    }

    @Test
    void usernameFallback_returnsUsername() {
        authenticate(jwt(null, null, "ada@test.com"));
        assertThat(auditor.getCurrentAuditor()).contains("ada@test.com");
    }

    @Test
    void unauthenticatedToken_returnsSystem() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("x", "y"));
        assertThat(auditor.getCurrentAuditor()).contains("system");
    }
}
