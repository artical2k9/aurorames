package com.mes.common.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtClaimsExtractorTest {

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }

    @Test
    void getRoles_returnsList() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "sub", "user-1",
                "org_id", "org-1",
                "roles", List.of("ROLE_OPERATOR", "ROLE_VIEWER"))));

        assertThat(extractor.getRoles()).containsExactlyInAnyOrder("ROLE_OPERATOR", "ROLE_VIEWER");
    }

    @Test
    void getRoles_missingClaim_returnsEmptyList() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "sub", "user-1",
                "org_id", "org-1")));

        assertThat(extractor.getRoles()).isEmpty();
    }

    @Test
    void getOrgId_present_returnsValue() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "sub", "user-1",
                "org_id", "org-abc",
                "roles", List.of())));

        assertThat(extractor.getOrgId()).isEqualTo("org-abc");
    }

    @Test
    void getOrgId_missing_throwsMissingClaimException() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "sub", "user-1",
                "roles", List.of())));

        assertThatThrownBy(extractor::getOrgId)
                .isInstanceOf(MissingClaimException.class)
                .hasMessageContaining("org_id");
    }

    @Test
    void getSub_returnsSubject() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "sub", "subject-xyz",
                "org_id", "org-1",
                "roles", List.of())));

        assertThat(extractor.getSub()).isEqualTo("subject-xyz");
    }

    @Test
    void nullSafeSubject_subPresent_returnsSub() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "sub", "user-uuid-123",
                "org_id", "org-1",
                "preferred_username", "admin@test.org")));

        assertThat(extractor.nullSafeSubject()).isEqualTo("user-uuid-123");
    }

    @Test
    void nullSafeSubject_subMissing_preferredUsernamePresent_returnsUsername() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of(
                "org_id", "org-1",
                "preferred_username", "admin@test.org")));

        assertThat(extractor.nullSafeSubject()).isEqualTo("admin@test.org");
    }

    @Test
    void nullSafeSubject_bothMissing_returnsUnknown() {
        var extractor = new JwtClaimsExtractor(jwt(Map.of("org_id", "org-1")));

        assertThat(extractor.nullSafeSubject()).isEqualTo("unknown");
    }
}
