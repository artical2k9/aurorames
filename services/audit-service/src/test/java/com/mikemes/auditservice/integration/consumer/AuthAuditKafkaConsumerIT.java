package com.mikemes.auditservice.integration.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mikemes.auditservice.repository.AuthAuditRecordRepository;
import com.mikemes.events.KafkaTopics;
import com.mikemes.events.audit.AuditEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuthAuditKafkaConsumerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mikemes")
            .withUsername("audit_service")
            .withPassword("test-password");

    @Container
    static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.consumer.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet"
                + ".OAuth2ResourceServerAutoConfiguration");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuthAuditRecordRepository authAuditRecordRepository;

    @Test
    void authEventPersistedAsAuthAuditRecord() throws Exception {
        UUID eventId = UUID.randomUUID();
        AuditEventMessage message = new AuditEventMessage(
            eventId,
            "AUTH_EVENT",
            "keycloak-spi",
            "USER",
            "user-auth-it-001",
            "user-auth-it-001",
            OffsetDateTime.now(ZoneOffset.UTC),
            "AUTH",
            Map.of("client_id", "mes-frontend", "ip_address", "10.0.0.1"),
            1
        );
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(message);

        kafkaTemplate.send(KafkaTopics.MES_AUDIT_EVENTS, "USER:user-auth-it-001", json);

        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(authAuditRecordRepository.findByEventId(eventId)).isPresent()
            );
    }
}
