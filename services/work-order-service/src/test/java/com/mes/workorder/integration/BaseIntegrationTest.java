package com.mes.workorder.integration;

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
import java.util.Set;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1,
        topics = {"iam.privilege-changes", "work-order.item-master.events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(BaseIntegrationTest.TestSecurityConfig.class)
public abstract class BaseIntegrationTest {

    // Generated once per JVM; all subclass test classes share the same key pair.
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

        // Overrides the auto-configured JwtDecoder so tokens signed with TEST_RSA_KEY
        // validate without contacting a real Keycloak issuer URI.
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            try {
                return NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey()).build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to build test JwtDecoder", e);
            }
        }

        // Replaces CaffeinePrivilegeCache so tests never call the IAM service.
        // Mirrors the privilege grants in Flyway V007 exactly.
        @Bean
        @Primary
        PrivilegeCache testPrivilegeCache() {
            return role -> switch (role) {
                case "SYSTEM_ADMIN" -> Set.of(
                        "item-master:records:view", "item-master:records:manage",
                        "item-master:bom:manage",  "item-master:eco:manage",
                        "item-master:udf:manage");
                case "ENGINEER" -> Set.of(
                        "item-master:records:view", "item-master:records:manage",
                        "item-master:bom:manage",  "item-master:eco:manage");
                default -> Set.of();
            };
        }
    }

    // No @Container — started once in static initializer so the Testcontainers JUnit 5
    // extension never stops/restarts it between test classes. A @Container static field
    // is stopped in afterAll() per class, which changes the port and breaks the cached
    // Spring context's HikariCP pool (ERR-MES-040). Ryuk cleans up on JVM exit.
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("work_order_user")
            .withPassword("secret");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // Mirror the production deployment order: IAM service runs its Flyway migrations
        // first, creating iam.privilege / iam.role / iam.role_privilege.  The work-order
        // service then runs its own migrations (including V007 which INSERTs into iam.*).
        // Without this step the test container starts empty and V007 fails with 42P01.
        bootstrapIamSchema();

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Use jwk-set-uri so Spring does not attempt OIDC discovery at startup.
        // Actual JWT validation is handled by the @Primary testJwtDecoder bean.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:1/jwks");
        registry.add("mes.security.iam-service-url", () -> "http://localhost:1");
        registry.add("mes.security.webhook-token", () -> "test-webhook-token");
    }

    private static void bootstrapIamSchema() {
        // IAM migration SQL files are copied to iam-bootstrap/ in the test classpath
        // by the processTestResources Gradle task in work-order-service/build.gradle.
        // This avoids filesystem path resolution issues across environments.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("iam")
                .locations("classpath:iam-bootstrap")
                .load()
                .migrate();
    }

    /**
     * Builds a self-signed JWT accepted by testJwtDecoder.
     * A random UUID subject is generated per call so tests that create multiple users
     * (e.g. differentJwtSubHasIndependentConfig) receive independent identities.
     */
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
}
