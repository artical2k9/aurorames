package com.mikemes.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.keycloak.client-id=admin-service",
        "spring.security.oauth2.client.registration.keycloak.client-secret=test-secret",
        "spring.security.oauth2.client.provider.keycloak.authorization-uri=http://localhost:8080/auth",
        "spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost:8080/token",
        "spring.security.oauth2.client.provider.keycloak.user-info-uri=http://localhost:8080/userinfo",
        "spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://localhost:8080/certs",
        "spring.boot.admin.client.enabled=false"
    }
)
class AdminServiceApplicationTest {

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void contextLoads() {
        // intentionally empty: verifies the Spring context loads without error
    }
}
