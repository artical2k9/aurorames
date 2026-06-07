package com.mes.platform.integration.uom;

import com.mes.common.security.privilege.PrivilegeCache;
import com.mes.platform.uom.api.dto.CreateUomRequest;
import com.mes.platform.uom.api.dto.PatchUomRequest;
import com.mes.platform.uom.api.dto.UnitOfMeasureDto;
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

import java.util.Arrays;
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
@Import(UnitOfMeasureControllerIT.TestSecurityConfig.class)
class UnitOfMeasureControllerIT {

    static final UUID SYSTEM_ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
                NimbusJwtDecoder decoder = NimbusJwtDecoder
                        .withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
                decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TEST_ISSUER));
                return decoder;
            } catch (JOSEException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @MockBean PrivilegeCache privilegeCache;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("mes.security.webhook-token", () -> "it-token");
        registry.add("mes.security.iam-service-url", () -> "http://localhost:8085");
    }

    @LocalServerPort int port;
    TestRestTemplate client;

    @BeforeEach
    void setUp() {
        client = new TestRestTemplate();
        when(privilegeCache.getPrivilegesForRole("SYSTEM_ADMIN"))
                .thenReturn(Set.of("settings:uom:read", "settings:uom:write"));
        when(privilegeCache.getPrivilegesForRole("VIEWER"))
                .thenReturn(Set.of());
    }

    // ── IT1: GET /uom returns seeded data including standard units ──────────────
    @Test
    void list_returnsSeededStandardUnits() {
        ResponseEntity<UnitOfMeasureDto[]> response = doGet("/uom", buildToken("SYSTEM_ADMIN"),
                UnitOfMeasureDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> codes = Arrays.stream(response.getBody()).map(UnitOfMeasureDto::code).toList();
        assertThat(codes).contains("KG", "M", "L", "S", "EA");
    }

    // ── IT2: GET /uom without privilege returns 403 ──────────────────────────────
    @Test
    void list_withoutPrivilege_returns403() {
        ResponseEntity<Map> response = doGet("/uom", buildToken("VIEWER"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── IT3: POST /uom creates a new entry → 201 ────────────────────────────────
    @Test
    void create_validRequest_returns201() {
        CreateUomRequest req = new CreateUomRequest("XT9", "XT", "Test unit IT", "Miscellaneous");

        ResponseEntity<UnitOfMeasureDto> response = client.exchange(
                url("/uom"), HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(buildToken("SYSTEM_ADMIN"))),
                UnitOfMeasureDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().code()).isEqualTo("XT9");
        assertThat(response.getBody().active()).isTrue();
    }

    // ── IT4: POST /uom with existing code returns 409 ───────────────────────────
    @Test
    void create_duplicateCode_returns409() {
        CreateUomRequest req = new CreateUomRequest("KG", "KGM", "Kilogram duplicate", "Mass");

        ResponseEntity<Map> response = client.exchange(
                url("/uom"), HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(buildToken("SYSTEM_ADMIN"))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── IT5: POST /uom missing required field returns 400 ───────────────────────
    @Test
    void create_missingName_returns400() {
        CreateUomRequest req = new CreateUomRequest("XZ1", null, null, "Miscellaneous");

        ResponseEntity<Map> response = client.exchange(
                url("/uom"), HttpMethod.POST,
                new HttpEntity<>(req, authHeaders(buildToken("SYSTEM_ADMIN"))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── IT6: PATCH /uom/{code} updates name → 200 ───────────────────────────────
    @Test
    void patch_updateName_returns200() {
        PatchUomRequest req = new PatchUomRequest(null, "Kilogramme (updated)", null);

        ResponseEntity<UnitOfMeasureDto> response = client.exchange(
                url("/uom/KG"), HttpMethod.PATCH,
                new HttpEntity<>(req, authHeaders(buildToken("SYSTEM_ADMIN"))),
                UnitOfMeasureDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Kilogramme (updated)");
    }

    // ── IT7: PATCH /uom/{code} deactivates entry; excluded from default list ────
    @Test
    void patch_deactivate_excludedFromDefaultList() {
        PatchUomRequest req = new PatchUomRequest(null, null, false);
        client.exchange(url("/uom/OZA"), HttpMethod.PATCH,
                new HttpEntity<>(req, authHeaders(buildToken("SYSTEM_ADMIN"))),
                UnitOfMeasureDto.class);

        ResponseEntity<UnitOfMeasureDto[]> active = doGet("/uom", buildToken("SYSTEM_ADMIN"),
                UnitOfMeasureDto[].class);
        List<String> codes = Arrays.stream(active.getBody()).map(UnitOfMeasureDto::code).toList();
        assertThat(codes).doesNotContain("OZA");

        ResponseEntity<UnitOfMeasureDto[]> all = doGet("/uom?includeInactive=true",
                buildToken("SYSTEM_ADMIN"), UnitOfMeasureDto[].class);
        List<String> allCodes = Arrays.stream(all.getBody()).map(UnitOfMeasureDto::code).toList();
        assertThat(allCodes).contains("OZA");
    }

    // ── IT8: PATCH /uom/{code} for unknown code returns 404 ─────────────────────
    @Test
    void patch_unknownCode_returns404() {
        PatchUomRequest req = new PatchUomRequest(null, "X", null);

        ResponseEntity<Map> response = client.exchange(
                url("/uom/ZZZNONE"), HttpMethod.PATCH,
                new HttpEntity<>(req, authHeaders(buildToken("SYSTEM_ADMIN"))),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String buildToken(String role) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER).subject("test-user")
                    .claim("org_id", SYSTEM_ORG.toString())
                    .claim("roles", List.of(role))
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
            jwt.sign(new RSASSASigner(TEST_RSA_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("buildToken failed", e);
        }
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.set("Content-Type", "application/json");
        return h;
    }

    private <T> ResponseEntity<T> doGet(String path, String token, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return client.exchange(url(path), HttpMethod.GET, new HttpEntity<>(h), type);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
