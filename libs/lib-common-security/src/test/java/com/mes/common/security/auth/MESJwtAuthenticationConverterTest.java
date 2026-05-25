package com.mes.common.security.auth;

import com.mes.common.security.privilege.PrivilegeCache;
import com.mes.common.security.privilege.PrivilegeGrantedAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MESJwtAuthenticationConverterTest {

    @Mock
    private PrivilegeCache privilegeCache;

    private MESJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MESJwtAuthenticationConverter(privilegeCache);
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }

    @Test
    void convert_returnsJwtAuthenticationTokenWithPrivilegeAuthorities() {
        when(privilegeCache.getPrivilegesForRole("ROLE_OPERATOR"))
                .thenReturn(Set.of("part.read", "workorder.execute"));
        when(privilegeCache.getPrivilegesForRole("ROLE_VIEWER"))
                .thenReturn(Set.of("part.read"));

        var token = (JwtAuthenticationToken) converter.convert(jwt(Map.of(
                "sub", "user-1",
                "org_id", "org-1",
                "roles", List.of("ROLE_OPERATOR", "ROLE_VIEWER"))));

        assertThat(token).isNotNull();
        var authorityKeys = token.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        assertThat(authorityKeys).containsExactlyInAnyOrder("part.read", "workorder.execute");
        assertThat(token.getAuthorities()).allMatch(a -> a instanceof PrivilegeGrantedAuthority);
    }

    @Test
    void convert_missingOrgId_throwsOAuth2AuthenticationException() {
        assertThatThrownBy(() -> converter.convert(jwt(Map.of(
                "sub", "user-1",
                "roles", List.of("ROLE_OPERATOR")))))
                .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }

    @Test
    void convert_emptyRoles_returnsEmptyAuthorities() {
        var token = (JwtAuthenticationToken) converter.convert(jwt(Map.of(
                "sub", "user-1",
                "org_id", "org-1",
                "roles", List.of())));

        assertThat(token.getAuthorities()).isEmpty();
    }
}
