package com.mes.inventory.integration;

import com.mes.common.security.privilege.PrivilegeCache;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1,
        topics = {"iam.privilege-changes", "inventory.item-master.events", "bom.released"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(BaseIntegrationTest.TestSecurityConfig.class)
public abstract class BaseIntegrationTest {

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

        // Mirrors V007/V013 grants.
        @Bean
        @Primary
        PrivilegeCache testPrivilegeCache() {
            return role -> switch (role) {
                case "SYSTEM_ADMIN" -> Set.of(
                        "item-master:records:view", "item-master:records:manage",
                        "item-master:revisions:approve",
                        "item-master:bom:manage",  "item-master:udf:manage",
                        "item-master:as5553:manage");
                case "ENGINEER" -> Set.of(
                        "item-master:records:view", "item-master:records:manage",
                        "item-master:bom:manage");
                case "QUALITY_ENGINEER" -> Set.of(
                        "item-master:records:view", "item-master:as5553:manage");
                default -> Set.of();
            };
        }

        @Bean
        BomReleasedCaptor bomReleasedCaptor() {
            return new BomReleasedCaptor();
        }
    }

    public static class BomReleasedCaptor {

        private final BlockingQueue<ConsumerRecord<String, String>> records =
                new LinkedBlockingQueue<>();

        @KafkaListener(topics = "bom.released", groupId = "test-bom-released-captor")
        public void capture(ConsumerRecord<String, String> evt) {
            records.add(evt);
        }

        public ConsumerRecord<String, String> poll(long timeout, TimeUnit unit)
                throws InterruptedException {
            return records.poll(timeout, unit);
        }
    }

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("inventory_user")
            .withPassword("secret");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        bootstrapIamSchema();

        registry.add("mes.bom.max-depth", () -> "3");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:1/jwks");
        registry.add("mes.security.iam-service-url", () -> "http://localhost:1");
        registry.add("mes.security.webhook-token", () -> "test-webhook-token");
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

    protected HttpEntity<?> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    protected static Map<String, Object> baseItemRequest(String partNumber, String revision) {
        return new HashMap<>(Map.of(
                "partNumber", partNumber,
                "revision", revision,
                "description", "Test item",
                "unitOfMeasure", "EA",
                "cageCode", "CAGE01",
                "classification", "FABRICATED",
                "makeBuyCode", "MAKE",
                "traceabilityMethod", "SERIAL"
        ));
    }
}
