package com.mes.udf.api;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtClaimsExtractorTest {

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }

    @Test
    void orgId_present_returnsUuid() {
        UUID expected = UUID.randomUUID();
        Jwt token = jwt(Map.of("org_id", expected.toString()));
        assertThat(JwtClaimsExtractor.orgId(token)).isEqualTo(expected);
    }

    @Test
    void orgId_missing_throwsBadRequest() {
        Jwt token = jwt(Map.of("sub", "user-1"));
        assertThatThrownBy(() -> JwtClaimsExtractor.orgId(token))
                .hasMessageContaining("org_id claim missing");
    }

    @Test
    void nullSafeSubject_subPresent_returnsSub() {
        Jwt token = jwt(Map.of("sub", "user-uuid-123", "org_id", "org-1"));
        assertThat(JwtClaimsExtractor.nullSafeSubject(token)).isEqualTo("user-uuid-123");
    }

    @Test
    void nullSafeSubject_subMissing_preferredUsernamePresent_returnsUsername() {
        Jwt token = jwt(Map.of("preferred_username", "admin@test.org", "org_id", "org-1"));
        assertThat(JwtClaimsExtractor.nullSafeSubject(token)).isEqualTo("admin@test.org");
    }

    @Test
    void nullSafeSubject_bothMissing_returnsUnknown() {
        Jwt token = jwt(Map.of("org_id", "org-1"));
        assertThat(JwtClaimsExtractor.nullSafeSubject(token)).isEqualTo("unknown");
    }
}
