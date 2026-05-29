package com.mes.workorder.integration.eco;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.workorder.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EcoKafkaIT extends BaseIntegrationTest {

    static final String TOPIC = "work-order.eco.events";
    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String ECO_BASE = "/api/v1/ecos";
    static final String ITEM_BASE = "/api/v1/item-master";

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void approveEcoPublishesEcoApprovedEvent() throws Exception {
        String token = buildToken(ORG_ID, List.of("ENGINEER"));
        String itemId = createItem(token, "ECOKFK-ITEM-001", "A");
        String ecoId = createEco(token, "Kafka ECO", itemId);

        try (KafkaConsumer<String, String> consumer = openConsumerAtEnd(TOPIC)) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ECO_BASE + "/" + ecoId + "/approve", HttpMethod.POST,
                    bearerRequest(token), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode event = pollForEvent(consumer, "ECO_APPROVED", 5);

            assertThat(event).isNotNull();
            assertThat(event.path("entityId").asText()).isEqualTo(ecoId);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HttpEntity<?> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String createItem(String token, String partNumber, String revision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest(partNumber, revision)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = response.getHeaders().getLocation().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String createEco(String token, String title, String itemId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                ECO_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("title", title, "affectedItemIds", List.of(itemId))),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private KafkaConsumer<String, String> openConsumerAtEnd(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "eco-test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private JsonNode pollForEvent(KafkaConsumer<String, String> consumer, String eventType, int timeoutSeconds)
            throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (var msg : records) {
                JsonNode node = objectMapper.readTree(msg.value());
                if (eventType.equals(node.path("eventType").asText())) {
                    return node;
                }
            }
        }
        return null;
    }
}
