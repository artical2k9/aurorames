package com.mes.labour.integration.kafka;

import com.mes.labour.integration.BaseIntegrationTest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the employee↔IAM unification: an {@code iam.user.created} event published by
 * iam-service results in a linked employee record being created in labour-service.
 */
class UserCreatedEventIT extends BaseIntegrationTest {

    private static final String ORG = "00000000-0000-0000-0000-000000000001";
    private static final String TOPIC = "iam.user.created";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private void publish(String json) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPIC, "key", json));
            producer.flush();
        }
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> getByIamUser(String token, String iamUserId) {
        return restTemplate.exchange(
                "/api/v1/labour/employees/by-iam-user/" + iamUserId, HttpMethod.GET,
                bearerRequest(token), new ParameterizedTypeReference<>() { });
    }

    @Test
    void userCreatedEvent_createsLinkedEmployee() {
        String token = buildToken(ORG, List.of("SYSTEM_ADMIN"));
        String userId = "kc-" + UUID.randomUUID();
        String empNo = "EMP-KAFKA-" + UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"userId\":\"" + userId + "\",\"orgId\":\"" + ORG
                + "\",\"email\":\"kafka@test.com\",\"firstName\":\"Kaf\",\"lastName\":\"Ka\","
                + "\"employeeNumber\":\"" + empNo + "\",\"hireDate\":\"2026-06-01\"}";

        publish(json);

        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() -> {
            ResponseEntity<Map<String, Object>> resp = getByIamUser(token, userId);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().get("employeeNumber")).isEqualTo(empNo);
            assertThat(resp.getBody().get("iamUserId")).isEqualTo(userId);
            assertThat(resp.getBody().get("firstName")).isEqualTo("Kaf");
        });
    }

    @Test
    void duplicateUserCreatedEvent_isIdempotent() {
        String token = buildToken(ORG, List.of("SYSTEM_ADMIN"));
        String userId = "kc-" + UUID.randomUUID();
        String empNo = "EMP-IDEM-" + UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"userId\":\"" + userId + "\",\"orgId\":\"" + ORG
                + "\",\"email\":\"idem@test.com\",\"firstName\":\"Id\",\"lastName\":\"Em\","
                + "\"employeeNumber\":\"" + empNo + "\"}";

        publish(json);
        publish(json); // redelivery — must not create a second record or error

        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() ->
                assertThat(getByIamUser(token, userId).getStatusCode()).isEqualTo(HttpStatus.OK));
        // Still resolvable to exactly one employee (no crash, no duplicate link).
        assertThat(getByIamUser(token, userId).getBody().get("employeeNumber")).isEqualTo(empNo);
    }
}
