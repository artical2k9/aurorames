package com.mes.common.security.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeycloakSupportTest {

    @RegisterExtension
    static KeycloakTestSupport keycloak = new KeycloakTestSupport();

    @Test
    void containerIsRunning() {
        assumeTrue(keycloak.isRunning(), "Docker not available — skipping");
        assertThat(keycloak.isRunning()).isTrue();
    }

    @Test
    void issuerUri_containsRealmName() {
        assumeTrue(keycloak.isRunning(), "Docker not available — skipping");
        assertThat(keycloak.issuerUri()).contains(KeycloakTestSupport.REALM);
    }

    @Test
    void createUser_andFetchToken_returnsJwt() {
        assumeTrue(keycloak.isRunning(), "Docker not available — skipping");

        keycloak.createUser("op-user", "secret", "org-1", List.of("ROLE_OPERATOR"));

        String token = keycloak.fetchToken("op-user", "secret");

        assertThat(token).isNotBlank().startsWith("ey");
    }
}
