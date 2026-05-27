package com.mes.workorder.integration.itemmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.workorder.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMasterKafkaIT extends BaseIntegrationTest {

    static final String TOPIC = "work-order.item-master.events";
    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createPublishesItemMasterCreatedEvent() throws Exception {
        String token = engineerToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/item-master",
                HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-EVT-001", "A")),
                Map.class);
        String entityId = extractIdFromLocation(created.getHeaders().getLocation().getPath());

        JsonNode event = pollForEvent(TOPIC, "ITEM_MASTER_CREATED", 5);

        assertThat(event).isNotNull();
        assertThat(event.path("entityId").asText()).isEqualTo(entityId);
    }

    @Test
    void patchPublishesItemMasterUpdatedEvent() throws Exception {
        String token = engineerToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/item-master",
                HttpMethod.POST,
                jsonRequest(token, createRequest("BRKT-EVT-002", "A")),
                Map.class);
        String itemId = extractIdFromLocation(created.getHeaders().getLocation().getPath());

        restTemplate.exchange(
                "/api/v1/item-master/" + itemId,
                HttpMethod.PATCH,
                jsonRequest(token, Map.of("description", "Updated")),
                Map.class);

        JsonNode event = pollForEvent(TOPIC, "ITEM_MASTER_UPDATED", 5);
        assertThat(event).isNotNull();
        assertThat(event.path("entityId").asText()).isEqualTo(itemId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private Map<String, Object> createRequest(String partNumber, String revision) {
        return new java.util.HashMap<>(Map.of(
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

    private HttpEntity<Map<String, Object>> jsonRequest(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private String extractIdFromLocation(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private JsonNode pollForEvent(String topic, String eventType, int timeoutSeconds) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<JsonNode> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (var record : records) {
                    JsonNode node = objectMapper.readTree(record.value());
                    if (eventType.equals(node.path("eventType").asText())) {
                        return node;
                    }
                }
            }
        }
        return null;
    }
}
