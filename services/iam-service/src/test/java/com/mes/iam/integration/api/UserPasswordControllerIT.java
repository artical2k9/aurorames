package com.mes.iam.integration.api;

import com.mes.iam.api.dto.CreateUserRequest;
import com.mes.iam.api.dto.SetPasswordRequest;
import com.mes.iam.api.dto.UserResponse;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes"})
@Import(UserPasswordControllerIT.TestJwtDecoderConfig.class)
class UserPasswordControllerIT {

    static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String TEST_REALM   = "mes-test";
    static final String TEST_ISSUER  = "http://test-keycloak/realms/" + TEST_REALM;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("iam_user")
            .withPassword("secret");

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer();

    static final com.nimbusds.jose.jwk.RSAKey TEST_RSA_KEY;

    static {
        try {
            TEST_RSA_KEY = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                    .keyID("test-key").generate();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            try {
                NimbusJwtDecoder decoder = NimbusJwtDecoder
                        .withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
                decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TEST_ISSUER));
                return decoder;
            } catch (com.nimbusds.jose.JOSEException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("keycloak.admin.realm", () -> TEST_REALM);
        registry.add("keycloak.admin.username", KEYCLOAK::getAdminUsername);
        registry.add("keycloak.admin.password", KEYCLOAK::getAdminPassword);
    }

    @Autowired TestRestTemplate restTemplate;

    static String adminToken;

    @BeforeAll
    static void setupKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available — skipping UserPasswordControllerIT");

        Keycloak kcAdmin = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();

        UserControllerIT.createRealm(kcAdmin);
        UserControllerIT.enableUnmanagedAttributes(kcAdmin);
        UserControllerIT.ensureRealmRole(kcAdmin, "SYSTEM_ADMIN");
        adminToken = UserControllerIT.buildToken("SYSTEM_ADMIN");
        kcAdmin.close();
    }

    // ── AS1: PUT /users/{id}/password with temporary=true → KC requiredActions contains
    //         UPDATE_PASSWORD, HTTP 204 ─────────────────────────────────────────────────
    @Test
    void resetPassword_temporary_returns204AndUserHasUpdatePasswordRequiredAction() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "pwreset-temp-" + UUID.randomUUID() + "@test.com";
        UserResponse created = createUser(email);

        SetPasswordRequest req = new SetPasswordRequest("NewPass1!", true);
        ResponseEntity<Void> response = put("/users/" + created.id() + "/password",
                adminToken, req, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify KC user has UPDATE_PASSWORD required action
        Keycloak kcAdmin = kcAdminClient();
        UserRepresentation u = kcAdmin.realm(TEST_REALM).users().get(created.id()).toRepresentation();
        assertThat(u.getRequiredActions()).contains("UPDATE_PASSWORD");
        kcAdmin.close();
    }

    // ── AS2: PUT /users/{id}/password with temporary=false → KC requiredActions does NOT
    //         contain UPDATE_PASSWORD, HTTP 204 ──────────────────────────────────────────
    @Test
    void resetPassword_permanent_returns204AndNoUpdatePasswordAction() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "pwreset-perm-" + UUID.randomUUID() + "@test.com";
        UserResponse created = createUser(email);

        // First set a temporary password so UPDATE_PASSWORD action exists
        setKcPassword(created.id(), "Temp1Pass!", true);

        // Now reset with temporary=false
        SetPasswordRequest req = new SetPasswordRequest("NewPerm1!", false);
        ResponseEntity<Void> response = put("/users/" + created.id() + "/password",
                adminToken, req, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Keycloak kcAdmin = kcAdminClient();
        UserRepresentation u = kcAdmin.realm(TEST_REALM).users().get(created.id()).toRepresentation();
        List<String> actions = u.getRequiredActions() != null ? u.getRequiredActions() : List.of();
        assertThat(actions).doesNotContain("UPDATE_PASSWORD");
        kcAdmin.close();
    }

    // ── AS3: PUT /users/{id}/password for user in a different org → HTTP 404 ───────────
    @Test
    void resetPassword_differentOrg_returns404() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        // randomUserId is not in SYSTEM_ORG_ID
        SetPasswordRequest req = new SetPasswordRequest("NewPass1!", false);

        ResponseEntity<Map> response = put("/users/" + UUID.randomUUID() + "/password",
                adminToken, req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── AS4: PUT /users/{id}/password with password < 8 chars → HTTP 400 validation ────
    @Test
    void resetPassword_shortPassword_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "pwreset-short-" + UUID.randomUUID() + "@test.com";
        UserResponse created = createUser(email);

        SetPasswordRequest req = new SetPasswordRequest("short", false);
        ResponseEntity<Map> response = put("/users/" + created.id() + "/password",
                adminToken, req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── AS5: PUT /users/{id}/password with no token → HTTP 401 ──────────────────────────
    @Test
    void resetPassword_noToken_returns401() {
        SetPasswordRequest req = new SetPasswordRequest("NewPass1!", false);

        ResponseEntity<Map> response = put("/users/" + UUID.randomUUID() + "/password",
                null, req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

    private UserResponse createUser(String email) {
        CreateUserRequest req = new CreateUserRequest(email, "Test", "User",
                List.of("SYSTEM_ADMIN"), null, false);
        return Objects.requireNonNull(
                restTemplate.exchange("/users", HttpMethod.POST,
                        new HttpEntity<>(req, bearerHeaders(adminToken)),
                        UserResponse.class).getBody());
    }

    private void setKcPassword(String userId, String password, boolean temporary) {
        Keycloak kcAdmin = kcAdminClient();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(temporary);
        kcAdmin.realm(TEST_REALM).users().get(userId).resetPassword(cred);
        kcAdmin.close();
    }

    private Keycloak kcAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();
    }

    private <T> ResponseEntity<T> put(String path, String token, Object body, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body, bearerHeaders(token)), type);
    }

    private static HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Content-Type", "application/json");
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }
}
