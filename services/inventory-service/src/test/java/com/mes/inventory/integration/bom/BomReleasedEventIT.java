package com.mes.inventory.integration.bom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.inventory.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1,
        topics = {"iam.privilege-changes", "inventory.item-master.events", "bom.released"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class BomReleasedEventIT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000002";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_BASE = "/api/v1/item-master";

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void releaseBomPublishesBomReleasedEventWithAllRequiredFields() throws Exception {
        String token = buildToken(ORG_ID, List.of("ENGINEER"));
        UUID ecoId = UUID.randomUUID();

        // Create parent item
        ResponseEntity<Map> itemResp = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest("EVT-PARENT-001", "A")), Map.class);
        assertThat(itemResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String parentPath = itemResp.getHeaders().getLocation().getPath();
        String parentItemId = parentPath.substring(parentPath.lastIndexOf('/') + 1);

        // Create BOM with ecoId
        ResponseEntity<Map> bomResp = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "parentItemId", parentItemId,
                        "bomRevision", "REV-A",
                        "ecoId", ecoId.toString())),
                Map.class);
        assertThat(bomResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bomId = bomResp.getBody().get("id").toString();

        // Set up Kafka consumer before releasing
        Properties consumerProps = new Properties();
        consumerProps.putAll(KafkaTestUtils.consumerProps(
                "bom-released-test-" + UUID.randomUUID(), "true", embeddedKafkaBroker));
        consumerProps.put("key.deserializer", StringDeserializer.class.getName());
        consumerProps.put("value.deserializer", StringDeserializer.class.getName());
        consumerProps.put("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("bom.released"));

            // Release the BOM
            ResponseEntity<Map> releaseResp = restTemplate.exchange(
                    BOM_BASE + "/" + bomId + "/release", HttpMethod.POST,
                    bearerRequest(token), Map.class);
            assertThat(releaseResp.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Poll for the event
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            ConsumerRecord<String, String> record = records.iterator().next();
            Map<?, ?> payload = objectMapper.readValue(record.value(), Map.class);

            assertThat(payload.get("bomId")).isEqualTo(bomId);
            assertThat(payload.get("ecoId")).isEqualTo(ecoId.toString());
            assertThat(payload.get("orgId")).isEqualTo(ORG_ID);
            assertThat(payload.get("parentItemId")).isEqualTo(parentItemId);
            assertThat(payload.get("bomRevision")).isEqualTo("REV-A");
        }
    }

    @Test
    void releaseBomWithNullEcoIdPublishesEventWithNullEcoId() throws Exception {
        String token = buildToken(ORG_ID, List.of("ENGINEER"));

        ResponseEntity<Map> itemResp = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest("EVT-NOECO-001", "A")), Map.class);
        String parentPath = itemResp.getHeaders().getLocation().getPath();
        String parentItemId = parentPath.substring(parentPath.lastIndexOf('/') + 1);

        ResponseEntity<Map> bomResp = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentItemId, "bomRevision", "REV-B")),
                Map.class);
        String bomId = bomResp.getBody().get("id").toString();

        Properties consumerProps = new Properties();
        consumerProps.putAll(KafkaTestUtils.consumerProps(
                "bom-released-noeco-" + UUID.randomUUID(), "true", embeddedKafkaBroker));
        consumerProps.put("key.deserializer", StringDeserializer.class.getName());
        consumerProps.put("value.deserializer", StringDeserializer.class.getName());
        consumerProps.put("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("bom.released"));

            restTemplate.exchange(BOM_BASE + "/" + bomId + "/release",
                    HttpMethod.POST, bearerRequest(token), Map.class);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            Map<?, ?> payload = objectMapper.readValue(records.iterator().next().value(), Map.class);
            assertThat(payload.get("bomId")).isEqualTo(bomId);
            assertThat(payload.get("ecoId")).isNull();
            assertThat(payload.get("parentItemId")).isEqualTo(parentItemId);
        }
    }

    private org.springframework.http.HttpEntity<?> bearerRequest(String token) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        return new org.springframework.http.HttpEntity<>(headers);
    }
}
