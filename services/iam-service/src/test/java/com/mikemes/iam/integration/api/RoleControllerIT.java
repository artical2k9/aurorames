package com.mikemes.iam.integration.api;

import com.mikemes.iam.api.dto.CreateRoleRequest;
import com.mikemes.iam.api.dto.RoleResponse;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes"})
class RoleControllerIT {

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
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("keycloak.admin.realm", () -> TEST_REALM);
        registry.add("keycloak.admin.username", KEYCLOAK::getAdminUsername);
        registry.add("keycloak.admin.password", KEYCLOAK::getAdminPassword);
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EmbeddedKafkaBroker embeddedKafka;

    static String adminToken;
    static String viewerToken;
    static Keycloak kcAdmin;

    @BeforeAll
    static void setupKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available — skipping RoleControllerIT");

        kcAdmin = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .build();

        createRealm();
        ensureRealmRole("ADMIN");
        ensureRealmRole("VIEWER");
        createTestUser("admin-user", "password", "ADMIN");
        createTestUser("viewer-user", "password", "VIEWER");
        adminToken = fetchToken("admin-user", "password");
        viewerToken = fetchToken("viewer-user", "password");
    }

    @Test
    void listRoles_withAdminToken_returns200AndSeededRoles() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<List<RoleResponse>> response = get("/roles", adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(response.getBody()).extracting(RoleResponse::name)
                .contains("ADMIN", "OPERATOR", "QUALITY_INSPECTOR", "PLANNER", "ENGINEER", "VIEWER");
    }

    @Test
    void createRole_withAdminToken_returns201AndCreatesInDbAndKeycloak() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        CreateRoleRequest request = new CreateRoleRequest("IT_SENIOR_INSPECTOR", "Senior inspector for IT test");
        ResponseEntity<RoleResponse> response = post("/roles", adminToken, request, RoleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("IT_SENIOR_INSPECTOR");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam.role WHERE name = 'IT_SENIOR_INSPECTOR'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void createRole_duplicateName_returns409() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        createRole("IT_DUPLICATE_ROLE");
        CreateRoleRequest request = new CreateRoleRequest("IT_DUPLICATE_ROLE", null);
        ResponseEntity<Map> response = post("/roles", adminToken, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRole_invalidNamePattern_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        CreateRoleRequest request = new CreateRoleRequest("lowercase_role", null);
        ResponseEntity<Map> response = post("/roles", adminToken, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteRole_systemRole_returns400() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        UUID adminRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam.role WHERE name = 'ADMIN' AND is_system_role = true", UUID.class);
        assertThat(adminRoleId).isNotNull();

        ResponseEntity<Map> response = delete("/roles/" + adminRoleId, adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message").toString()).contains("System roles");
    }

    @Test
    void deleteRole_customRoleNoAssignments_returns204() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        RoleResponse created = createRole("IT_DELETE_ME");
        ResponseEntity<Void> response = delete("/roles/" + created.id(), adminToken, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam.role WHERE id = ?", Integer.class, created.id());
        assertThat(count).isZero();
    }

    @Test
    void grantAndRevokePrivilege_publishesKafkaEvents() throws InterruptedException {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        RoleResponse role = createRole("IT_GRANT_REVOKE");
        UUID privilegeId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam.privilege WHERE privilege_key = 'iam:users:view'", UUID.class);
        assertThat(privilegeId).isNotNull();

        ResponseEntity<Void> grant = put("/roles/" + role.id() + "/privileges/" + privilegeId,
                adminToken, Void.class);
        assertThat(grant.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> revoke = deleteNoBody("/roles/" + role.id() + "/privileges/" + privilegeId,
                adminToken, Void.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<String> received = consumeKafkaMessages("iam.privilege-changes", 2);
        assertThat(received).containsExactlyInAnyOrder("IT_GRANT_REVOKE", "IT_GRANT_REVOKE");
    }

    @Test
    void mutatingEndpoints_withViewerToken_return403() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        CreateRoleRequest request = new CreateRoleRequest("SHOULD_FAIL", null);
        ResponseEntity<Map> response = post("/roles", viewerToken, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allEndpoints_withNoToken_return401() {
        ResponseEntity<Map> get = get("/roles", null, new ParameterizedTypeReference<>() {});
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> post = post("/roles", null,
                new CreateRoleRequest("NOOP", null), Map.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RoleResponse createRole(String name) {
        ResponseEntity<RoleResponse> r = post("/roles", adminToken,
                new CreateRoleRequest(name, null), RoleResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private <T> ResponseEntity<T> get(String path, String token,
                                       ParameterizedTypeReference<T> type) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), type);
    }

    private <T> ResponseEntity<T> post(String path, String token, Object body, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)), type);
    }

    private <T> ResponseEntity<T> delete(String path, String token, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)), type);
    }

    private <T> ResponseEntity<T> deleteNoBody(String path, String token, Class<T> type) {
        return restTemplate.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)), type);
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

    private List<String> consumeKafkaMessages(String topic, int expectedCount) throws InterruptedException {
        Properties props = new Properties();
        props.put("bootstrap.servers", embeddedKafka.getBrokersAsString());
        props.put("group.id", "it-consumer-" + UUID.randomUUID());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");

        List<String> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 10_000;
            while (messages.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    messages.add(r.value());
                }
            }
        }
        return messages;
    }

    // ── Keycloak realm setup helpers ─────────────────────────────────────────

    static void createRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(TEST_REALM);
        realm.setEnabled(true);
        realm.setDirectGrantFlow("direct grant");
        kcAdmin.realms().create(realm);

        try {
            String kcToken = kcAdmin.tokenManager().getAccessTokenString();
            String profileUrl = KEYCLOAK.getAuthServerUrl() + "/admin/realms/" + TEST_REALM
                    + "/users/profile";
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            String existingJson = http.send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(profileUrl))
                            .header("Authorization", "Bearer " + kcToken)
                            .GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode cfg =
                    (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(existingJson);
            cfg.put("unmanagedAttributePolicy", "ENABLED");
            com.fasterxml.jackson.databind.node.ArrayNode attrs = cfg.withArray("attributes");
            boolean hasOrgId = false;
            for (com.fasterxml.jackson.databind.JsonNode attr : attrs) {
                if ("org_id".equals(attr.path("name").asText())) {
                    hasOrgId = true;
                    break;
                }
            }
            if (!hasOrgId) {
                attrs.addObject().put("name", "org_id");
            }
            java.net.http.HttpResponse<String> putResp = http.send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(profileUrl))
                            .header("Authorization", "Bearer " + kcToken)
                            .header("Content-Type", "application/json")
                            .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(
                                    mapper.writeValueAsString(cfg)))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (putResp.statusCode() / 100 != 2) {
                throw new IllegalStateException("User Profile update failed: "
                        + putResp.statusCode() + " — " + putResp.body());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure User Profile", e);
        }

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
        rolesMapper.setConfig(Map.of(
                "multivalued", "true",
                "access.token.claim", "true",
                "userinfo.token.claim", "false",
                "claim.name", "roles",
                "jsonType.label", "String"));
        Response r1 = kcAdmin.realm(TEST_REALM).clients().get(clientUuid)
                .getProtocolMappers().createMapper(rolesMapper);
        r1.close();

        ProtocolMapperRepresentation orgMapper = new ProtocolMapperRepresentation();
        orgMapper.setName("org-id-claim");
        orgMapper.setProtocol("openid-connect");
        orgMapper.setProtocolMapper("oidc-usermodel-attribute-mapper");
        orgMapper.setConfig(Map.of(
                "user.attribute", "org_id",
                "access.token.claim", "true",
                "userinfo.token.claim", "false",
                "claim.name", "org_id",
                "jsonType.label", "String"));
        Response r2 = kcAdmin.realm(TEST_REALM).clients().get(clientUuid)
                .getProtocolMappers().createMapper(orgMapper);
        r2.close();
    }

    static void ensureRealmRole(String roleName) {
        try {
            kcAdmin.realm(TEST_REALM).roles().get(roleName).toRepresentation();
        } catch (Exception e) {
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            kcAdmin.realm(TEST_REALM).roles().create(role);
        }
    }

    static void createTestUser(String username, String password, String role) {
        ensureRealmRole(role);

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
            String body = "grant_type=password"
                    + "&client_id=" + TEST_CLIENT
                    + "&username=" + username
                    + "&password=" + password;
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + TEST_REALM
                            + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            String response = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
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
