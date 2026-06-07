package com.mes.platform.integration.api;

import com.mes.common.security.privilege.PrivilegeCache;
import com.mes.platform.api.dto.UpsertConfigRequest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
    }
)
@Testcontainers(disabledWithoutDocker = true)
@Import(SystemConfigControllerIT.TestSecurityConfig.class)
class SystemConfigControllerIT {

    static final UUID ORG_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ORG_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final String WEBHOOK_TOKEN = "it-webhook-token-for-platform";
    static final String TEST_ISSUER = "http://test-keycloak/realms/mes-test";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("platform_user")
            .withPassword("secret");

    static final RSAKey TEST_RSA_KEY;

    static {
        try {
            TEST_RSA_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            try {
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
                decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TEST_ISSUER));
                return decoder;
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @MockBean
    PrivilegeCache privilegeCache;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("mes.security.webhook-token", () -> WEBHOOK_TOKEN);
        registry.add("mes.security.iam-service-url", () -> "http://localhost:8085");
    }

    @LocalServerPort
    int port;

    TestRestTemplate client;

    @BeforeEach
    void setUp() {
        client = new TestRestTemplate();
        when(privilegeCache.getPrivilegesForRole("SYSTEM_ADMIN"))
                .thenReturn(Set.of("platform:config:manage", "platform:config:read"));
        when(privilegeCache.getPrivilegesForRole("VIEWER"))
                .thenReturn(Set.of("platform:config:read"));
    }

    @Test
    void put_upsert_returns200_and_get_returns_value() {
        String key = "test.upsert.key";
        UpsertConfigRequest req = new UpsertConfigRequest("upsert-value", "Test entry");
        String adminToken = buildToken(ORG_A, "SYSTEM_ADMIN");

        ResponseEntity<Map> putResponse = doPut(key, req, adminToken);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = doGet(key, adminToken);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("value", "upsert-value");
    }

    @Test
    void get_other_org_returns_404() {
        String key = "test.org-isolation.key";
        String adminTokenA = buildToken(ORG_A, "SYSTEM_ADMIN");
        String adminTokenB = buildToken(ORG_B, "SYSTEM_ADMIN");

        doPut(key, new UpsertConfigRequest("org-a-value", null), adminTokenA);

        ResponseEntity<Map> response = doGet(key, adminTokenB);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void put_without_manage_privilege_returns_403() {
        String key = "test.privilege.key";
        String viewerToken = buildToken(ORG_A, "VIEWER");

        ResponseEntity<Map> response = doPut(key, new UpsertConfigRequest("val", null), viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void put_is_idempotent() {
        String key = "test.idempotent.key";
        String adminToken = buildToken(ORG_A, "SYSTEM_ADMIN");

        doPut(key, new UpsertConfigRequest("first-value", null), adminToken);
        ResponseEntity<Map> second = doPut(key, new UpsertConfigRequest("second-value", null), adminToken);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).containsEntry("value", "second-value");
    }

    @Test
    void delete_sets_active_false() {
        String key = "test.delete.key";
        String adminToken = buildToken(ORG_A, "SYSTEM_ADMIN");

        doPut(key, new UpsertConfigRequest("to-delete", null), adminToken);

        ResponseEntity<Void> deleteResponse = doDelete(key, adminToken);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = doGet(key, adminToken);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void v001_migration_applies_cleanly() {
        assertThat(POSTGRES.isRunning()).isTrue();
        ResponseEntity<List> list = doList(buildToken(ORG_A, "SYSTEM_ADMIN"));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String buildToken(UUID orgId, String role) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER)
                    .subject("test-user")
                    .claim("org_id", orgId.toString())
                    .claim("roles", List.of(role))
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                    claims);
            jwt.sign(new RSASSASigner(TEST_RSA_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("buildToken failed", e);
        }
    }

    private ResponseEntity<Map> doGet(String key, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return client.exchange(
                "http://localhost:" + port + "/config/" + key,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<List> doList(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return client.exchange(
                "http://localhost:" + port + "/config/",
                HttpMethod.GET, new HttpEntity<>(headers), List.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> doPut(String key, UpsertConfigRequest body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Content-Type", "application/json");
        return client.exchange(
                "http://localhost:" + port + "/config/" + key,
                HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Void> doDelete(String key, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return client.exchange(
                "http://localhost:" + port + "/config/" + key,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
