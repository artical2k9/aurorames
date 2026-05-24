package com.mikemes.auditservice.integration.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.events.KafkaTopics;
import com.mikemes.events.audit.AuditEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(AuditKafkaConsumerIT.TestJwtConfig.class)
class AuditKafkaConsumerIT {

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "test")
                .build();
        }
    }

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
                + ".OAuth2ResourceServerAutoConfiguration,"
                + "com.mikemes.common.security.config.MikeMESSecurityAutoConfiguration");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AuditRecordRepository repository;

    @Test
    void consumedEventPersistedWithinFiveSeconds() throws Exception {
        UUID eventId = UUID.randomUUID();
        AuditEventMessage message = new AuditEventMessage(
            eventId, "KAFKA_EVENT", "integration-test",
            "WorkOrder", "WO-IT-001", "user:it-test",
            OffsetDateTime.now(ZoneOffset.UTC), "CREATE",
            Map.of("status", "OPEN"), 1
        );
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(message);

        kafkaTemplate.send(KafkaTopics.MES_AUDIT_EVENTS,
            "WorkOrder:WO-IT-001", json);

        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(repository.findByEventId(eventId)).isPresent()
            );
    }

    @Test
    void duplicateEventIdIsIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        AuditEventMessage message = new AuditEventMessage(
            eventId, "KAFKA_EVENT", "integration-test",
            "WorkOrder", "WO-IT-002", "user:it-test",
            OffsetDateTime.now(ZoneOffset.UTC), "UPDATE",
            Map.of(), 1
        );
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(message);

        kafkaTemplate.send(KafkaTopics.MES_AUDIT_EVENTS, "WorkOrder:WO-IT-002", json);
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(repository.findByEventId(eventId)).isPresent()
            );

        kafkaTemplate.send(KafkaTopics.MES_AUDIT_EVENTS, "WorkOrder:WO-IT-002", json);

        Thread.sleep(1000);
        long count = repository.findAll().stream()
            .filter(r -> eventId.equals(r.getEventId()))
            .count();
        assertThat(count).isEqualTo(1);
    }
}
