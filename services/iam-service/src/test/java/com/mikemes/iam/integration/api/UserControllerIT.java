package com.mikemes.iam.integration.api;

import com.mikemes.iam.api.dto.CreateUserRequest;
import com.mikemes.iam.api.dto.UpdateUserRolesRequest;
import com.mikemes.iam.api.dto.UserResponse;
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
class UserControllerIT {

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
        ensureRealmRole(kcAdmin, "ADMIN");
        ensureRealmRole(kcAdmin, "VIEWER");
        createTestUser(kcAdmin, "admin-user", "password", "ADMIN");
        createTestUser(kcAdmin, "viewer-user", "password", "VIEWER");
        adminToken = fetchToken("admin-user", "password");
        viewerToken = fetchToken("viewer-user", "password");
        kcAdmin.close();
    }

    @Test
    void createUser_withAdminToken_returns201AndUserAppearsInKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");
        String email = "invite-" + UUID.randomUUID() + "@test.com";
        CreateUserRequest request = new CreateUserRequest(email, "Alice", "Smith", List.of("ADMIN"));

        ResponseEntity<UserResponse> response = post("/users", adminToken, request, UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse body = Objects.requireNonNull(response.getBody());
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.firstName()).isEqualTo("Alice");
        assertThat(body.enabled()).isTrue();
        assertThat(body.roles()).contains("ADMIN");
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
        CreateUserRequest request = new CreateUserRequest("not-an-email", "A", "B", List.of("ADMIN"));

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
                        new CreateUserRequest(email, "Get", "Me", List.of("ADMIN")),
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
                        new CreateUserRequest(email, "Role", "User", List.of("ADMIN")),
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
        user.setAttributes(Map.of("org_id", List.of(SYSTEM_ORG_ID.toString())));
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        user.setCredentials(List.of(cred));
        Response r = kcAdmin.realm(TEST_REALM).users().create(user);
        String userId = extractId(r);
        r.close();
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
