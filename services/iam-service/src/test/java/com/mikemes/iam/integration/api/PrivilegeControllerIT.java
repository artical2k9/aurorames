package com.mikemes.iam.integration.api;

import com.mikemes.iam.api.dto.PrivilegeItemRequest;
import com.mikemes.iam.api.dto.RegisterPrivilegesRequest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes"})
class PrivilegeControllerIT {

    static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String TEST_REALM = "mikemes-test";
    static final String TEST_CLIENT = "test-client";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mikemes")
            .withUsername("iam_user")
            .withPassword("secret");

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "/realms/" + TEST_REALM
                        + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "/realms/" + TEST_REALM);
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
        ensureRealmRole(kcAdmin, "ADMIN");
        ensureRealmRole(kcAdmin, "VIEWER");
        createTestUser(kcAdmin, "admin-user", "password", "ADMIN");
        createTestUser(kcAdmin, "viewer-user", "password", "VIEWER");
        adminToken = fetchToken("admin-user", "password");
        viewerToken = fetchToken("viewer-user", "password");
        kcAdmin.close();
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

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(TEST_CLIENT);
        client.setPublicClient(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setEnabled(true);
        Response r = kcAdmin.realm(TEST_REALM).clients().create(client);
        String clientUuid = extractId(r);
        r.close();

        ProtocolMapperRepresentation rolesMapper = new ProtocolMapperRepresentation();
        rolesMapper.setName("roles-claim");
        rolesMapper.setProtocol("openid-connect");
        rolesMapper.setProtocolMapper("oidc-usermodel-realm-role-mapper");
        rolesMapper.setConfig(Map.of("multivalued", "true", "access.token.claim", "true",
                "userinfo.token.claim", "false", "claim.name", "roles", "jsonType.label", "String"));
        Response r1 = kcAdmin.realm(TEST_REALM).clients().get(clientUuid)
                .getProtocolMappers().createMapper(rolesMapper);
        r1.close();

        ProtocolMapperRepresentation orgMapper = new ProtocolMapperRepresentation();
        orgMapper.setName("org-id-claim");
        orgMapper.setProtocol("openid-connect");
        orgMapper.setProtocolMapper("oidc-usermodel-attribute-mapper");
        orgMapper.setConfig(Map.of("user.attribute", "org_id", "access.token.claim", "true",
                "userinfo.token.claim", "false", "claim.name", "org_id", "jsonType.label", "String"));
        Response r2 = kcAdmin.realm(TEST_REALM).clients().get(clientUuid)
                .getProtocolMappers().createMapper(orgMapper);
        r2.close();
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

    static void createTestUser(Keycloak kcAdmin, String username, String password, String role) {
        ensureRealmRole(kcAdmin, role);
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmail(username + "@test.mikemes.local");
        user.setEmailVerified(true);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAttributes(Map.of("org_id", List.of(SYSTEM_ORG_ID.toString())));
        Response r = kcAdmin.realm(TEST_REALM).users().create(user);
        String userId = extractId(r);
        r.close();

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        kcAdmin.realm(TEST_REALM).users().get(userId).resetPassword(cred);

        RoleRepresentation roleRep = kcAdmin.realm(TEST_REALM).roles().get(role).toRepresentation();
        kcAdmin.realm(TEST_REALM).users().get(userId).roles().realmLevel().add(List.of(roleRep));
    }

    static String fetchToken(String username, String password) {
        try {
            String body = "grant_type=password&client_id=" + TEST_CLIENT
                    + "&username=" + username + "&password=" + password;
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + TEST_REALM
                            + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            String response = java.net.http.HttpClient.newHttpClient()
                    .send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response).get("access_token");
            if (node == null) {
                throw new IllegalStateException("No access_token in response: " + response);
            }
            return node.asText();
        } catch (Exception e) {
            throw new IllegalStateException("fetchToken failed", e);
        }
    }

    private static String extractId(Response response) {
        String location = response.getHeaderString("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
