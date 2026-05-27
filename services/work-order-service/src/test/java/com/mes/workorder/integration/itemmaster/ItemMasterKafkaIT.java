package com.mes.workorder.integration.itemmaster;

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
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
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
        // Position consumer at end BEFORE creating so earlier-test events are skipped.
        try (KafkaConsumer<String, String> consumer = openConsumerAtEnd(TOPIC)) {
            ResponseEntity<Map> created = restTemplate.exchange(
                    "/api/v1/item-master",
                    HttpMethod.POST,
                    jsonRequest(token, baseItemRequest("BRKT-EVT-001", "A")),
                    Map.class);
            String entityId = extractIdFromLocation(created.getHeaders().getLocation().getPath());

            JsonNode event = pollForEvent(consumer, "ITEM_MASTER_CREATED", 5);

            assertThat(event).isNotNull();
            assertThat(event.path("entityId").asText()).isEqualTo(entityId);
        }
    }

    @Test
    void patchPublishesItemMasterUpdatedEvent() throws Exception {
        String token = engineerToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/item-master",
                HttpMethod.POST,
                jsonRequest(token, baseItemRequest("BRKT-EVT-002", "A")),
                Map.class);
        String itemId = extractIdFromLocation(created.getHeaders().getLocation().getPath());

        // Position consumer at end AFTER create (skips CREATED event) and BEFORE patch.
        try (KafkaConsumer<String, String> consumer = openConsumerAtEnd(TOPIC)) {
            restTemplate.exchange(
                    "/api/v1/item-master/" + itemId,
                    HttpMethod.PATCH,
                    jsonRequest(token, Map.of("description", "Updated")),
                    Map.class);


            JsonNode event = pollForEvent(consumer, "ITEM_MASTER_UPDATED", 5);
            assertThat(event).isNotNull();
            assertThat(event.path("entityId").asText()).isEqualTo(itemId);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private String extractIdFromLocation(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // Opens a consumer with manual partition assignment (synchronous, no group coordinator
    // roundtrip) and seeks to end. Calls position() on each partition to force eager
    // materialization of the lazy seekToEnd — consumer is firmly positioned at end
    // BEFORE the caller's test action publishes any events.
    private KafkaConsumer<String, String> openConsumerAtEnd(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position); // force eager materialization
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
