package com.mes.workorder.integration;

import com.mes.common.security.test.KeycloakTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1,
        topics = {"iam.privilege-changes", "work-order.item-master.events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public abstract class BaseIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("work_order_user")
            .withPassword("secret");

    @RegisterExtension
    protected static final KeycloakTestSupport KEYCLOAK = new KeycloakTestSupport();

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
        // Bypass Keycloak OIDC discovery — use KeycloakTestSupport issuer URI when available
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.isRunning() ? KEYCLOAK.issuerUri() : "http://localhost:1/realms/mes-test");
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

    @Autowired
    protected TestRestTemplate restTemplate;
}
