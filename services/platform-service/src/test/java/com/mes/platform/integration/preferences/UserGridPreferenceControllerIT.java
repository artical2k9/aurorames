package com.mes.platform.integration.preferences;

import com.mes.common.security.privilege.PrivilegeCache;
import com.mes.platform.preferences.api.dto.ColumnPreferenceEntry;
import com.mes.platform.preferences.api.dto.UpsertUserGridPreferenceRequest;
import com.mes.platform.preferences.api.dto.UserGridPreferenceDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri="
    }
)
@Testcontainers(disabledWithoutDocker = true)
@Import(UserGridPreferenceControllerIT.TestSecurityConfig.class)
class UserGridPreferenceControllerIT {

    static final UUID ORG_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ORG_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

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
                return NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @MockitoBean
    PrivilegeCache privilegeCache;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("mes.security.webhook-token", () -> "it-webhook-token");
        registry.add("mes.security.iam-service-url", () -> "http://localhost:8085");
    }

    @LocalServerPort
    int port;

    TestRestTemplate client;

    @BeforeEach
    void setUp() {
        client = new TestRestTemplate();
    }

    @Test
    void get_returns_defaults_for_new_user() {
        String token = buildToken(ORG_A, "user-get-defaults");

        ResponseEntity<UserGridPreferenceDto> response = doGet("ITEM_MASTER", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().moduleKey()).isEqualTo("ITEM_MASTER");
        assertThat(response.getBody().columns()).isNotEmpty();
    }

    @Test
    void put_saves_column_config_and_get_returns_saved() {
        String token = buildToken(ORG_A, "user-put-get");
        List<ColumnPreferenceEntry> columns = List.of(
                new ColumnPreferenceEntry("partNumber", true, 0),
                new ColumnPreferenceEntry("description", false, 1)
        );
        UpsertUserGridPreferenceRequest request = new UpsertUserGridPreferenceRequest(columns);

        ResponseEntity<UserGridPreferenceDto> putResponse = doPut("ITEM_MASTER", request, token);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody()).isNotNull();
        assertThat(putResponse.getBody().columns()).hasSize(2);

        ResponseEntity<UserGridPreferenceDto> getResponse = doGet("ITEM_MASTER", token);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().columns()).hasSize(2);
        assertThat(getResponse.getBody().columns().get(0).columnKey()).isEqualTo("partNumber");
    }

    @Test
    void put_is_idempotent() {
        String token = buildToken(ORG_A, "user-idempotent");
        List<ColumnPreferenceEntry> first = List.of(new ColumnPreferenceEntry("partNumber", true, 0));
        List<ColumnPreferenceEntry> second = List.of(new ColumnPreferenceEntry("revision", true, 0));

        doPut("ITEM_MASTER", new UpsertUserGridPreferenceRequest(first), token);
        ResponseEntity<UserGridPreferenceDto> response = doPut("ITEM_MASTER",
                new UpsertUserGridPreferenceRequest(second), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().columns().get(0).columnKey()).isEqualTo("revision");
    }

    @Test
    void org_isolation_different_users_see_own_preferences() {
        String tokenA = buildToken(ORG_A, "user-iso-a");
        String tokenB = buildToken(ORG_B, "user-iso-b");
        List<ColumnPreferenceEntry> colsA = List.of(new ColumnPreferenceEntry("partNumber", true, 0));
        List<ColumnPreferenceEntry> colsB = List.of(new ColumnPreferenceEntry("status", true, 0));

        doPut("ITEM_MASTER", new UpsertUserGridPreferenceRequest(colsA), tokenA);
        doPut("ITEM_MASTER", new UpsertUserGridPreferenceRequest(colsB), tokenB);

        ResponseEntity<UserGridPreferenceDto> responseA = doGet("ITEM_MASTER", tokenA);
        assertThat(responseA.getBody().columns().get(0).columnKey()).isEqualTo("partNumber");

        ResponseEntity<UserGridPreferenceDto> responseB = doGet("ITEM_MASTER", tokenB);
        assertThat(responseB.getBody().columns().get(0).columnKey()).isEqualTo("status");
    }

    @Test
    void get_without_token_returns_401() {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<Void> response = client.exchange(
                "http://localhost:" + port + "/api/v1/users/preferences/grid/ITEM_MASTER",
                HttpMethod.GET, new HttpEntity<>(headers), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String buildToken(UUID orgId, String userId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim("org_id", orgId.toString())
                    .claim("roles", List.of("USER"))
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

    private ResponseEntity<UserGridPreferenceDto> doGet(String moduleKey, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return client.exchange(
                "http://localhost:" + port + "/api/v1/users/preferences/grid/" + moduleKey,
                HttpMethod.GET, new HttpEntity<>(headers), UserGridPreferenceDto.class);
    }

    private ResponseEntity<UserGridPreferenceDto> doPut(String moduleKey,
                                                         UpsertUserGridPreferenceRequest body,
                                                         String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Content-Type", "application/json");
        return client.exchange(
                "http://localhost:" + port + "/api/v1/users/preferences/grid/" + moduleKey,
                HttpMethod.PUT, new HttpEntity<>(body, headers), UserGridPreferenceDto.class);
    }
}
