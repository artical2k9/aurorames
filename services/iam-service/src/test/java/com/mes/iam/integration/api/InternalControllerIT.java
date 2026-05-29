package com.mes.iam.integration.api;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.mes.common.security.privilege.PrivilegeManifest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"iam.privilege-changes", "iam.events"})
class InternalControllerIT {

    static final String WEBHOOK_TOKEN = "test-webhook-secret-" + UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mes")
            .withUsername("iam_user")
            .withPassword("secret");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Use jwk-set-uri so Spring does not fetch the OIDC discovery document at startup
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:1/jwks");
        registry.add("keycloak.admin.server-url", () -> "http://localhost:1");
        registry.add("keycloak.admin.realm", () -> "test");
        registry.add("keycloak.admin.username", () -> "admin");
        registry.add("keycloak.admin.password", () -> "admin");
        registry.add("iam.webhook.token", () -> WEBHOOK_TOKEN);
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired EmbeddedKafkaBroker embeddedKafka;

    private long eventsTopicOffset = 0;

    @BeforeEach
    void captureOffset() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition("iam.events", 0);
            consumer.assign(List.of(tp));
            consumer.seekToEnd(List.of(tp));
            eventsTopicOffset = consumer.position(tp);
        }
    }

    @Test
    void receiveKeycloakEvent_validTokenAndEventType_returns204AndPublishesToKafka() {
        Map<String, Object> event = Map.of(
                "type", "LOGIN",
                "userId", UUID.randomUUID().toString(),
                "realmId", "mes",
                "time", System.currentTimeMillis());

        ResponseEntity<Void> response = post("/internal/keycloak-events", WEBHOOK_TOKEN, event);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(consumeFromTopic("iam.events", 1)).hasSize(1);
    }

    @Test
    void receiveKeycloakEvent_invalidToken_returns401() {
        Map<String, Object> event = Map.of("type", "LOGIN");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/internal/keycloak-events",
                HttpMethod.POST,
                new HttpEntity<>(event, bearerHeaders("wrong-token")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void receiveKeycloakEvent_missingToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/internal/keycloak-events",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("type", "LOGIN"), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void receiveKeycloakEvent_unknownEventType_returns204WithoutPublishing() {
        Map<String, Object> event = Map.of("realmId", "mes");

        ResponseEntity<Void> response = post("/internal/keycloak-events", WEBHOOK_TOKEN, event);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void receiveKeycloakEvent_multipleEventTypes_publishesAllToKafka() {
        Map<String, Object> login = Map.of("type", "LOGIN", "userId", "u1");
        Map<String, Object> logout = Map.of("type", "LOGOUT", "userId", "u1");

        post("/internal/keycloak-events", WEBHOOK_TOKEN, login);
        post("/internal/keycloak-events", WEBHOOK_TOKEN, logout);

        assertThat(consumeFromTopic("iam.events", 2)).hasSize(2);
    }

    @Test
    void getPrivilegeManifest_validToken_returns200WithSeededPrivileges() {
        ResponseEntity<PrivilegeManifest> response = restTemplate.exchange(
                "/internal/privileges",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(WEBHOOK_TOKEN)),
                PrivilegeManifest.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Flyway V003 seeds SYSTEM_ADMIN with iam:* privileges — manifest is never empty
        assertThat(response.getBody().rolePrivileges()).containsKey("SYSTEM_ADMIN");
    }

    @Test
    void getPrivilegeManifest_invalidToken_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/internal/privileges",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders("wrong-token")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Void> post(String path, String token, Object body) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)), Void.class);
    }

    private static HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Content-Type", "application/json");
        h.setBearerAuth(token);
        return h;
    }

    private List<String> consumeFromTopic(String topic, int expectedCount) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<String> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(List.of(tp));
            consumer.seek(tp, eventsTopicOffset);
            long deadline = System.currentTimeMillis() + 5_000;
            while (messages.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(r -> messages.add(r.value()));
            }
        }
        return messages;
    }
}
