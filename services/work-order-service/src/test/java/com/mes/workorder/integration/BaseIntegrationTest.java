package com.mes.workorder.integration;

import com.mes.common.security.test.KeycloakTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("work_order_user")
            .withPassword("secret");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.7.1").asCompatibleSubstituteFor("confluentinc/cp-kafka"));

    @RegisterExtension
    static final KeycloakTestSupport KEYCLOAK = new KeycloakTestSupport();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Bypass Keycloak OIDC discovery — use KeycloakTestSupport issuer URI when available
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.isRunning() ? KEYCLOAK.issuerUri() : "http://localhost:1/realms/mes-test");
        registry.add("mes.security.iam-service-url", () -> "http://localhost:1");
        registry.add("mes.security.webhook-token", () -> "test-webhook-token");
    }

    @Autowired
    protected TestRestTemplate restTemplate;
}
