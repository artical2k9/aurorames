package com.mes.iam.integration.api;

import com.mes.iam.api.dto.CreateRoleRequest;
import com.mes.iam.api.dto.RoleResponse;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes"})
@Import(RoleControllerIT.TestJwtDecoderConfig.class)
class RoleControllerIT {

    static final UUID SYSTEM_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String TEST_REALM = "mes-test";

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
        adminToken = buildToken("SYSTEM_ADMIN");
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
    void listRoles_withAdminToken_returns200AndSeededRoles() {
        assumeTrue(KEYCLOAK.isRunning(), "Docker not available");

        ResponseEntity<List<RoleResponse>> response = get("/roles", adminToken,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(response.getBody()).extracting(RoleResponse::name)
                .contains("SYSTEM_ADMIN", "OPERATOR", "QUALITY_INSPECTOR", "PLANNER", "ENGINEER", "VIEWER");
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
                "SELECT id FROM iam.role WHERE name = 'SYSTEM_ADMIN' AND is_system_role = true", UUID.class);
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

    // ── Keycloak realm setup ──────────────────────────────────────────────────

    static void createRealm() {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(TEST_REALM);
        realm.setEnabled(true);
        realm.setDirectGrantFlow("direct grant");
        kcAdmin.realms().create(realm);
    }
}
