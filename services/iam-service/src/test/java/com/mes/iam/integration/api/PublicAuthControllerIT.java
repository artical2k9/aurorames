package com.mes.iam.integration.api;

import com.mes.iam.api.dto.ChangeTemporaryPasswordRequest;
import com.mes.iam.api.dto.CreateUserRequest;
import com.mes.iam.api.dto.UserResponse;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
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
import org.springframework.http.MediaType;
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
@Import(PublicAuthControllerIT.TestJwtDecoderConfig.class)
class PublicAuthControllerIT {

    static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String TEST_REALM   = "mes-test";
    static final String TEST_ISSUER  = "http://test-keycloak/realms/" + TEST_REALM;
    static final String DIRECT_GRANT_CLIENT = "mes-frontend";

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
        registry.add("keycloak.direct-grant-client-id", () -> DIRECT_GRANT_CLIENT);
    }

    @Autowired TestRestTemplate restTemplate;

    static String adminToken;

    @BeforeAll
    static void setupKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available — skipping PublicAuthControllerIT");

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
        createDirectGrantClient(kcAdmin);
        adminToken = UserControllerIT.buildToken("SYSTEM_ADMIN");
        kcAdmin.close();
    }

    // ── AS1: valid username + correct temp password + new password → HTTP 204;
    //         KC user has no UPDATE_PASSWORD required action afterwards ──────────────────
    @Test
    void changeTemporaryPassword_validCredentials_returns204AndClearsRequiredAction() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "changepw-" + UUID.randomUUID() + "@test.com";
        String userId = createUserAndSetTempPassword(email, "TempPass1!");

        ChangeTemporaryPasswordRequest req =
                new ChangeTemporaryPasswordRequest(email, "TempPass1!", "NewPerm1!");
        ResponseEntity<Void> response = postPublic("/auth/change-temporary-password", req, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Keycloak kcAdmin = kcAdminClient();
        UserRepresentation u = kcAdmin.realm(TEST_REALM).users().get(userId).toRepresentation();
        List<String> actions = u.getRequiredActions() != null ? u.getRequiredActions() : List.of();
        assertThat(actions).doesNotContain("UPDATE_PASSWORD");
        kcAdmin.close();
    }

    // ── AS2: valid username + wrong password → HTTP 400 with generic message ──────────
    @Test
    void changeTemporaryPassword_wrongPassword_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "changepw-wrong-" + UUID.randomUUID() + "@test.com";
        createUserAndSetTempPassword(email, "TempPass1!");

        ChangeTemporaryPasswordRequest req =
                new ChangeTemporaryPasswordRequest(email, "WrongPass1!", "NewPerm1!");
        ResponseEntity<Map> response = postPublic("/auth/change-temporary-password", req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── AS3: unknown username → HTTP 400 (same generic message — no enumeration) ──────
    @Test
    void changeTemporaryPassword_unknownUser_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        ChangeTemporaryPasswordRequest req = new ChangeTemporaryPasswordRequest(
                "nobody-" + UUID.randomUUID() + "@test.com", "SomePass1!", "NewPerm1!");

        ResponseEntity<Map> response = postPublic("/auth/change-temporary-password", req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── AS4: user exists with permanent password (no UPDATE_PASSWORD action) → HTTP 400 ─
    @Test
    void changeTemporaryPassword_permanentPassword_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "changepw-perm-" + UUID.randomUUID() + "@test.com";
        String userId = createUserWithPermanentPassword(email, "PermPass1!");

        ChangeTemporaryPasswordRequest req =
                new ChangeTemporaryPasswordRequest(email, "PermPass1!", "NewPerm1!");
        ResponseEntity<Map> response = postPublic("/auth/change-temporary-password", req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify user was untouched — still no UPDATE_PASSWORD (was already permanent)
        Keycloak kcAdmin = kcAdminClient();
        UserRepresentation u = kcAdmin.realm(TEST_REALM).users().get(userId).toRepresentation();
        List<String> actions = u.getRequiredActions() != null ? u.getRequiredActions() : List.of();
        assertThat(actions).doesNotContain("UPDATE_PASSWORD");
        kcAdmin.close();
    }

    // ── AS5: new password < 8 chars → HTTP 400 validation ───────────────────────────────
    @Test
    void changeTemporaryPassword_shortNewPassword_returns400() {
        ChangeTemporaryPasswordRequest req =
                new ChangeTemporaryPasswordRequest("user@test.com", "TempPass1!", "short");

        ResponseEntity<Map> response = postPublic("/auth/change-temporary-password", req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

    private String createUserAndSetTempPassword(String email, String tempPassword) {
        CreateUserRequest req = new CreateUserRequest(email, "Test", "User",
                List.of("SYSTEM_ADMIN"), null, false);
        UserResponse user = Objects.requireNonNull(
                restTemplate.exchange("/users", HttpMethod.POST,
                        new HttpEntity<>(req, adminBearerHeaders()),
                        UserResponse.class).getBody());

        Keycloak kcAdmin = kcAdminClient();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(tempPassword);
        cred.setTemporary(true);
        kcAdmin.realm(TEST_REALM).users().get(user.id()).resetPassword(cred);
        kcAdmin.close();
        return user.id();
    }

    private String createUserWithPermanentPassword(String email, String password) {
        CreateUserRequest req = new CreateUserRequest(email, "Test", "User",
                List.of("SYSTEM_ADMIN"), null, false);
        UserResponse user = Objects.requireNonNull(
                restTemplate.exchange("/users", HttpMethod.POST,
                        new HttpEntity<>(req, adminBearerHeaders()),
                        UserResponse.class).getBody());

        Keycloak kcAdmin = kcAdminClient();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        kcAdmin.realm(TEST_REALM).users().get(user.id()).resetPassword(cred);
        kcAdmin.close();
        return user.id();
    }

    static void createDirectGrantClient(Keycloak kcAdmin) {
        boolean exists = kcAdmin.realm(TEST_REALM).clients()
                .findByClientId(DIRECT_GRANT_CLIENT)
                .stream().anyMatch(c -> DIRECT_GRANT_CLIENT.equals(c.getClientId()));
        if (exists) {
            return;
        }
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(DIRECT_GRANT_CLIENT);
        client.setPublicClient(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setEnabled(true);
        kcAdmin.realm(TEST_REALM).clients().create(client);
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

    private <T> ResponseEntity<T> postPublic(String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), type);
    }

    private HttpHeaders adminBearerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Content-Type", "application/json");
        h.setBearerAuth(adminToken);
        return h;
    }
}
