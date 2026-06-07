package com.mes.iam.integration.api;

import com.mes.iam.api.dto.CreateUserRequest;
import com.mes.iam.api.dto.UpdateUserRolesRequest;
import com.mes.iam.api.dto.UserResponse;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes"})
@Import(UserControllerIT.TestJwtDecoderConfig.class)
class UserControllerIT {

    static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String TEST_REALM = "mes-test";
    static final String TEST_ISSUER = "http://test-keycloak/realms/" + TEST_REALM;

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
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
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
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "");
        registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("keycloak.admin.realm", () -> TEST_REALM);
        registry.add("keycloak.admin.username", KEYCLOAK::getAdminUsername);
        registry.add("keycloak.admin.password", KEYCLOAK::getAdminPassword);
    }

    @Autowired TestRestTemplate restTemplate;

    static String adminToken;
    static String viewerToken;

    @BeforeAll
    static void setupKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available — skipping UserControllerIT");

        Keycloak kcAdmin = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();

        createRealm(kcAdmin);
        enableUnmanagedAttributes(kcAdmin);
        ensureRealmRole(kcAdmin, "SYSTEM_ADMIN");
        ensureRealmRole(kcAdmin, "VIEWER");
        adminToken = buildToken("SYSTEM_ADMIN");
        viewerToken = buildToken("VIEWER");
        kcAdmin.close();
    }

    static String buildToken(String role) {
        try {
            com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER)
                    .subject("test-user")
                    .claim("org_id", SYSTEM_ORG_ID.toString())
                    .claim("roles", List.of(role))
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                    .build();
            com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(
                    new com.nimbusds.jose.JWSHeader.Builder(
                            com.nimbusds.jose.JWSAlgorithm.RS256).keyID("test-key").build(),
                    claims);
            jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(TEST_RSA_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("buildToken failed", e);
        }
    }

    @Test
    void createUser_withAdminToken_returns201AndUserAppearsInKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "invite-" + UUID.randomUUID() + "@test.com";
        CreateUserRequest request = new CreateUserRequest(email, "Alice", "Smith", List.of("SYSTEM_ADMIN"));

        ResponseEntity<UserResponse> response = post("/users", adminToken, request, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse body = Objects.requireNonNull(response.getBody());
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.firstName()).isEqualTo("Alice");
        assertThat(body.enabled()).isTrue();
        assertThat(body.roles()).contains("SYSTEM_ADMIN");
    }

    @Test
    void createUser_duplicateEmail_returns409() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        CreateUserRequest request = new CreateUserRequest(email, "Bob", "Jones", List.of("VIEWER"));

        post("/users", adminToken, request, UserResponse.class);
        ResponseEntity<Map> second = post("/users", adminToken, request, Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createUser_withViewerToken_returns403() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        CreateUserRequest request = new CreateUserRequest(
                "x-" + UUID.randomUUID() + "@test.com", "X", "Y", List.of("VIEWER"));

        ResponseEntity<Map> response = post("/users", viewerToken, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createUser_noToken_returns401() {
        CreateUserRequest request = new CreateUserRequest("a@b.com", "A", "B", List.of("VIEWER"));

        ResponseEntity<Map> response = post("/users", null, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createUser_invalidEmail_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        CreateUserRequest request = new CreateUserRequest("not-an-email", "A", "B", List.of("SYSTEM_ADMIN"));

        ResponseEntity<Map> response = post("/users", adminToken, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listUsers_withAdminToken_returns200AndIncludesCreatedUser() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "list-" + UUID.randomUUID() + "@test.com";
        post("/users", adminToken,
                new CreateUserRequest(email, "List", "User", List.of("VIEWER")), UserResponse.class);

        ResponseEntity<List<UserResponse>> response = get("/users", adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UserResponse> users = Objects.requireNonNull(response.getBody());
        assertThat(users).extracting(UserResponse::email).contains(email);
    }

    @Test
    void listUsers_withViewerToken_returns403() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<Map> response = get("/users", viewerToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getUser_withAdminToken_returns200() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "get-" + UUID.randomUUID() + "@test.com";
        UserResponse created = Objects.requireNonNull(
                post("/users", adminToken,
                        new CreateUserRequest(email, "Get", "Me", List.of("SYSTEM_ADMIN")),
                        UserResponse.class).getBody());

        ResponseEntity<UserResponse> response = get("/users/" + created.id(), adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(response.getBody()).email()).isEqualTo(email);
    }

    @Test
    void getUser_unknownId_returns404() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<Map> response = get("/users/" + UUID.randomUUID(), adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void setUserRoles_withAdminToken_returns200WithUpdatedRoles() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "roles-" + UUID.randomUUID() + "@test.com";
        UserResponse created = Objects.requireNonNull(
                post("/users", adminToken,
                        new CreateUserRequest(email, "Role", "User", List.of("SYSTEM_ADMIN")),
                        UserResponse.class).getBody());

        UpdateUserRolesRequest req = new UpdateUserRolesRequest(List.of("VIEWER"));
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/users/" + created.id() + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(req, bearerHeaders(adminToken)),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(response.getBody()).roles()).containsExactly("VIEWER");
    }

    @Test
    void deactivateUser_withAdminToken_returns204AndUserDisabled() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "deactivate-" + UUID.randomUUID() + "@test.com";
        UserResponse created = Objects.requireNonNull(
                post("/users", adminToken,
                        new CreateUserRequest(email, "Deact", "User", List.of("VIEWER")),
                        UserResponse.class).getBody());

        ResponseEntity<Void> deactivateResponse = post(
                "/users/" + created.id() + "/deactivate", adminToken, null, Void.class);

        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<UserResponse> getResponse = get("/users/" + created.id(), adminToken,
                new ParameterizedTypeReference<>() {});
        assertThat(Objects.requireNonNull(getResponse.getBody()).enabled()).isFalse();
    }

    @Test
    void deactivateUser_noToken_returns401() {
        ResponseEntity<Map> response = post(
                "/users/" + UUID.randomUUID() + "/deactivate", null, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private <T> ResponseEntity<T> get(String path, String token,
                                       ParameterizedTypeReference<T> type) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), type);
    }

    private <T> ResponseEntity<T> post(String path, String token, Object body, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.POST,
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

    // ── Keycloak setup helpers ────────────────────────────────────────────────

    static void createRealm(Keycloak kcAdmin) {
        boolean exists = kcAdmin.realms().findAll().stream()
                .anyMatch(r -> TEST_REALM.equals(r.getRealm()));
        if (exists) {
            return;
        }
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(TEST_REALM);
        realm.setEnabled(true);
        realm.setDirectGrantFlow("direct grant");
        kcAdmin.realms().create(realm);
    }

    static void enableUnmanagedAttributes(Keycloak kcAdmin) {
        // KC 24+ defaults unmanaged attribute policy to DISABLED; org_id is not declared
        // in the user profile schema so it would be silently dropped on create/read.
        // Enable unmanaged attributes so the Admin REST API can set and retrieve org_id.
        try {
            UPConfig config = kcAdmin.realm(TEST_REALM).users().userProfile().getConfiguration();
            config.setUnmanagedAttributePolicy(UPConfig.UnmanagedAttributePolicy.ENABLED);
            kcAdmin.realm(TEST_REALM).users().userProfile().update(config);
        } catch (RuntimeException e) {
            // if the KC version in the container predates this API, tests will fail naturally
        }
    }

    static void ensureRealmRole(Keycloak kcAdmin, String roleName) {
        try {
            kcAdmin.realm(TEST_REALM).roles().get(roleName).toRepresentation();
        } catch (RuntimeException e) {
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            kcAdmin.realm(TEST_REALM).roles().create(role);
        }
    }
}
