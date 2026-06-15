package com.mes.routing.integration;

import com.mes.common.security.privilege.PrivilegeCache;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(BaseIntegrationTest.TestSecurityConfig.class)
public abstract class BaseIntegrationTest {

    protected static final String DEV_ORG = "00000000-0000-0000-0000-000000000001";

    static final RSAKey TEST_RSA_KEY;

    static {
        try {
            TEST_RSA_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test RSA key", e);
        }
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            try {
                return NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to build test JwtDecoder", e);
            }
        }

        @Bean
        @Primary
        PrivilegeCache testPrivilegeCache() {
            return role -> switch (role) {
                case "SYSTEM_ADMIN" -> Set.of(
                        "routing:route:view",     "routing:route:manage",
                        "routing:route:approve",  "routing:operation:approve",
                        "routing:settings:view",  "routing:settings:manage");
                case "ENGINEER" -> Set.of(
                        "routing:route:view", "routing:route:manage", "routing:settings:view");
                default -> Set.of();
            };
        }
    }

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("routing_user")
            .withPassword("secret");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        bootstrapIamSchema();

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:1/jwks");
        registry.add("mes.security.iam-service-url", () -> "http://localhost:1");
        // Exclude MESSecurityAutoConfiguration to prevent UnreachableFilterChainException (ERR-MES-038)
        registry.add("spring.autoconfigure.exclude",
                () -> "com.mes.common.security.config.MESSecurityAutoConfiguration");
    }

    private static void bootstrapIamSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("iam")
                .locations("classpath:iam-bootstrap")
                .load()
                .migrate();
    }

    protected static String buildToken(String orgId, List<String> roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("test-user-" + UUID.randomUUID())
                    .claim("org_id", orgId)
                    .claim("roles", roles)
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

    @Autowired
    protected TestRestTemplate restTemplate;

    protected HttpEntity<Map<String, Object>> jsonRequest(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    protected HttpEntity<Void> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
