package com.mikemes.iam.integration.api;

import com.mikemes.iam.api.dto.PrivilegeItemRequest;
import com.mikemes.iam.api.dto.RegisterPrivilegesRequest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
@Import(PrivilegeControllerIT.TestJwtDecoderConfig.class)
class PrivilegeControllerIT {

    static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String TEST_REALM = "mikemes-test";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mikemes")
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
        JwtDecoder testJwtDecoder() {
            try {
                return NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
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
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("keycloak.admin.realm", () -> TEST_REALM);
        registry.add("keycloak.admin.username", KEYCLOAK::getAdminUsername);
        registry.add("keycloak.admin.password", KEYCLOAK::getAdminPassword);
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    static String adminToken;
    static String viewerToken;

    @BeforeAll
    static void setupKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available — skipping PrivilegeControllerIT");

        Keycloak kcAdmin = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();

        createRealm(kcAdmin);
        adminToken = buildToken("ADMIN");
        viewerToken = buildToken("VIEWER");
        kcAdmin.close();
    }

    static String buildToken(String role) {
        try {
            com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
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
    void registerPrivileges_validManifest_returns204() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        RegisterPrivilegesRequest request = new RegisterPrivilegesRequest(
                "quality",
                List.of(new PrivilegeItemRequest("quality:inspection:view", "View inspections")));

        ResponseEntity<Void> response = post("/privileges/register", adminToken, request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void registerPrivileges_calledTwice_isIdempotent() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        RegisterPrivilegesRequest request = new RegisterPrivilegesRequest(
                "quality",
                List.of(new PrivilegeItemRequest("quality:inspection:sign-off", "Sign off")));

        post("/privileges/register", adminToken, request, Void.class);
        post("/privileges/register", adminToken, request, Void.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam.privilege WHERE privilege_key = 'quality:inspection:sign-off'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void registerPrivileges_invalidKeyFormat_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        RegisterPrivilegesRequest request = new RegisterPrivilegesRequest(
                "quality",
                List.of(new PrivilegeItemRequest("InvalidKey", "Bad format")));

        ResponseEntity<Map> response = post("/privileges/register", adminToken, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerPrivileges_noToken_returns401() {
        RegisterPrivilegesRequest request = new RegisterPrivilegesRequest(
                "quality",
                List.of(new PrivilegeItemRequest("quality:inspection:view", "View")));

        ResponseEntity<Map> response = post("/privileges/register", null, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listPrivileges_withAdminToken_returns200WithIamModule() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<Map> response = get("/privileges", adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("iam");
    }

    @Test
    void listPrivileges_withViewerToken_returns403() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<Map> response = get("/privileges", viewerToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getPrivilegeMap_withAdminToken_returns200WithAdminRole() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<Map> response = get("/roles/privilege-map", adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> body = (Map<String, List<String>>) Objects.requireNonNull(response.getBody());
        assertThat(body).containsKey("ADMIN");
        List<String> adminPrivileges = body.get("ADMIN");
        assertThat(adminPrivileges).contains("iam:roles:manage");
    }

    @Test
    void getPrivilegeMap_noToken_returns401() {
        ResponseEntity<Map> response = get("/roles/privilege-map", null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getPrivilegeMap_reflectsGrantImmediately() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        RegisterPrivilegesRequest registerReq = new RegisterPrivilegesRequest(
                "test-module",
                List.of(new PrivilegeItemRequest("test-module:feature:read", "Read feature")));
        post("/privileges/register", adminToken, registerReq, Void.class);

        UUID privilegeId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam.privilege WHERE privilege_key = 'test-module:feature:read'",
                UUID.class);

        UUID viewerRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam.role WHERE name = 'VIEWER' AND is_system_role = true",
                UUID.class);

        assertThat(privilegeId).isNotNull();
        assertThat(viewerRoleId).isNotNull();

        put("/roles/" + viewerRoleId + "/privileges/" + privilegeId, adminToken, Void.class);

        ResponseEntity<Map> response = get("/roles/privilege-map", adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> privilegeMapBody =
                (Map<String, List<String>>) Objects.requireNonNull(response.getBody());
        List<String> viewerPrivileges = privilegeMapBody.get("VIEWER");
        assertThat(viewerPrivileges).contains("test-module:feature:read");
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

    private <T> ResponseEntity<T> put(String path, String token, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(bearerHeaders(token)), type);
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
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(TEST_REALM);
        realm.setEnabled(true);
        realm.setDirectGrantFlow("direct grant");
        kcAdmin.realms().create(realm);
    }
}
